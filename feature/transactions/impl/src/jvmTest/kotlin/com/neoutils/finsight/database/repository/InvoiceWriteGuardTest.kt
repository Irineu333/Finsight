package com.neoutils.finsight.database.repository

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.neoutils.finsight.database.AppDatabase
import com.neoutils.finsight.database.entity.AccountEntity
import com.neoutils.finsight.database.entity.CreditCardEntity
import com.neoutils.finsight.database.entity.DimensionEntity
import com.neoutils.finsight.database.entity.InvoiceEntity
import com.neoutils.finsight.database.mapper.RecurringMapper
import com.neoutils.finsight.database.mapper.TransactionMapper
import com.neoutils.finsight.domain.error.ClosedAccountException
import com.neoutils.finsight.domain.error.ClosedFacade
import com.neoutils.finsight.domain.error.InvoiceLockedException
import com.neoutils.finsight.domain.error.LedgerError
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.DimensionKind
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.TransactionIntent
import com.neoutils.finsight.domain.model.TransactionLeg
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.IInvoiceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * The invoice-status invariant at the single write boundary (design D23).
 *
 * `CLOSED` and `PAID` are deliberately *not* the same gate: a closed invoice must
 * still accept the payment that settles it, which is the whole point of closing.
 * A design that fused them would pass every test here but the third.
 */
class InvoiceWriteGuardTest {

    private val db = Room.inMemoryDatabaseBuilder<AppDatabase>()
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()

    @AfterTest fun tearDown() = db.close()

    private val card = CreditCard(
        id = 1,
        name = "Card",
        limit = 1000.0,
        closingDay = 10,
        dueDay = 20,
        accountId = 2,
    )

    private val payer = Account(id = 1, name = "Checking", type = AccountType.ASSET)

    /** A second open account, so an edit has somewhere to retarget to. */
    private val other = Account(id = 3, name = "Savings", type = AccountType.ASSET)

    private fun invoice(status: Invoice.Status) = Invoice(
        id = 1,
        creditCard = card,
        dimensionId = 1,
        openingMonth = YearMonth(2026, 2),
        closingMonth = YearMonth(2026, 3),
        dueMonth = YearMonth(2026, 3),
        status = status,
    )

    private var seeded = false

    /** The fixture is shared, so a test may build a repository more than once. */
    private suspend fun repository(status: Invoice.Status): TransactionRepository {
        if (!seeded) {
            seeded = true
            seed()
        }
        return transactionRepository(status)
    }

    private suspend fun seed() {
        db.accountDao().insert(AccountEntity(id = 1, name = "Checking", type = AccountEntity.Type.ASSET))
        db.accountDao().insert(AccountEntity(id = 2, name = "Card", type = AccountEntity.Type.LIABILITY))
        db.accountDao().insert(AccountEntity(id = 3, name = "Savings", type = AccountEntity.Type.ASSET))
        db.creditCardDao().insert(
            CreditCardEntity(id = 1, name = "Card", limit = 1000.0, closingDay = 10, dueDay = 20, accountId = 2)
        )
        db.dimensionDao().insert(DimensionEntity(id = 1, kind = DimensionKind.INVOICE))
        db.invoiceDao().insert(
            InvoiceEntity(
                id = 1,
                creditCardId = 1,
                dimensionId = 1,
                openingMonth = YearMonth(2026, 2),
                closingMonth = YearMonth(2026, 3),
                dueMonth = YearMonth(2026, 3),
                status = InvoiceEntity.Status.OPEN,
            )
        )
    }

    private fun transactionRepository(status: Invoice.Status) = TransactionRepository(
            database = db,
            transactionDao = db.transactionDao(),
            entryDao = db.entryDao(),
            recurringDao = db.recurringDao(),
            categoryRepository = FakeCategoryRepository,
            creditCardRepository = FakeCreditCardRepository,
            invoiceRepository = SingleInvoiceRepository(invoice(status)),
            installmentRepository = FakeInstallmentRepository,
            accountRepository = LedgerAccountRepository(db),
            transactionMapper = TransactionMapper(),
            recurringMapper = RecurringMapper(),
            ledgerEntryWriter = LedgerEntryWriter(db.entryDao(), db.accountDao(), db.creditCardDao(), db.dimensionDao()),
    )

    private fun purchase() = TransactionIntent(
        title = "Groceries",
        date = LocalDate(2026, 3, 5),
        legs = listOf(
            TransactionLeg(
                type = TransactionType.EXPENSE,
                amount = 50.0,
                creditCard = card,
                invoice = invoice(Invoice.Status.OPEN),
            )
        ),
    )

    private fun payment() = TransactionIntent(
        title = null,
        date = LocalDate(2026, 3, 15),
        legs = listOf(
            TransactionLeg(
                type = TransactionType.EXPENSE,
                amount = 50.0,
                account = payer,
                creditCard = card,
                invoice = invoice(Invoice.Status.CLOSED),
            ),
            TransactionLeg(
                type = TransactionType.INCOME,
                amount = 50.0,
                creditCard = card,
                invoice = invoice(Invoice.Status.CLOSED),
            ),
        ),
    )

    @Test
    fun `an open invoice accepts a new expense`() = runTest {
        val transaction = repository(Invoice.Status.OPEN).createTransaction(purchase())
        assertEquals(2, transaction.entries.size)
    }

    @Test
    fun `a closed invoice refuses a new expense`() = runTest {
        val error = assertFailsWith<InvoiceLockedException> {
            repository(Invoice.Status.CLOSED).createTransaction(purchase())
        }
        assertEquals(LedgerError.ClosedInvoice, error.error)
    }

    @Test
    fun `a closed invoice accepts the payment that settles it`() = runTest {
        val transaction = repository(Invoice.Status.CLOSED).createTransaction(payment())
        assertEquals(2, transaction.entries.size)
    }

    @Test
    fun `a paid invoice refuses a new expense`() = runTest {
        val error = assertFailsWith<InvoiceLockedException> {
            repository(Invoice.Status.PAID).createTransaction(purchase())
        }
        assertEquals(LedgerError.PaidInvoice, error.error)
    }

    @Test
    fun `a paid invoice refuses removal, in bulk as well as one by one`() = runTest {
        val repository = repository(Invoice.Status.OPEN)
        val purchase = repository.createTransaction(purchase())

        val locked = repository(Invoice.Status.PAID)

        assertFailsWith<InvoiceLockedException> { locked.deleteTransactionById(purchase.id) }
        // The bulk path is the one an installment deletion takes; it used to reach
        // the row removal without passing the gate at all.
        assertFailsWith<InvoiceLockedException> { locked.deleteTransactionsByIds(listOf(purchase.id)) }
    }

    @Test
    fun `a closed account refuses removal, in bulk as well as one by one`() = runTest {
        // The inconsistency this closes: archiving an ASSET/LIABILITY requires a zero
        // balance, and nothing stopped a removal from reopening one afterwards. The
        // account then takes no entries and shows in no selector, so the balance
        // could never be zeroed again.
        val repository = repository(Invoice.Status.OPEN)
        val spend = repository.createTransaction(
            TransactionIntent(
                title = "Groceries",
                date = LocalDate(2026, 3, 5),
                legs = listOf(TransactionLeg(type = TransactionType.EXPENSE, amount = 10.0, account = payer)),
            )
        )
        db.accountDao().close(1)

        val error = assertFailsWith<ClosedAccountException> {
            repository.deleteTransactionById(spend.id)
        }
        assertEquals(LedgerError.ClosedAccountRemoval(ClosedFacade.ACCOUNT), error.error)
        assertFailsWith<ClosedAccountException> {
            repository.deleteTransactionsByIds(listOf(spend.id))
        }
        assertEquals(2, repository.getTransactionById(spend.id)?.entries?.size)
    }

    @Test
    fun `a closed account refuses having a transaction retargeted off it`() = runTest {
        // The sharpest form of the hole: no removal, no write to the closed account —
        // the edit just points the leg at a different account. The rewrite deletes
        // the old legs, so the archived account's balance changes without a single
        // entry ever being written to it, and every new leg is open so the write
        // boundary waves it through.
        val repository = repository(Invoice.Status.OPEN)
        val spend = repository.createTransaction(
            TransactionIntent(
                title = "Groceries",
                date = LocalDate(2026, 3, 5),
                legs = listOf(TransactionLeg(type = TransactionType.EXPENSE, amount = 10.0, account = payer)),
            )
        )
        db.accountDao().close(1)

        val error = assertFailsWith<ClosedAccountException> {
            repository.updateTransaction(
                id = spend.id,
                title = "Moved",
                date = LocalDate(2026, 3, 6),
                leg = TransactionLeg(type = TransactionType.EXPENSE, amount = 10.0, account = other),
            )
        }
        assertEquals(LedgerError.ClosedAccountRemoval(ClosedFacade.ACCOUNT), error.error)
        // The legs are untouched: the balance the archive precondition guaranteed
        // is still there.
        assertEquals(-1000L, db.entryDao().balanceOf(1))
    }

    @Test
    fun `a closed category does not block removal`() = runTest {
        // A category closes at any balance — its balance is a period total, not money
        // sitting anywhere — so removing its movement strands nothing. Guarding it
        // would make the guard a blanket rule instead of the mirror of the
        // archive precondition.
        val repository = repository(Invoice.Status.OPEN)
        val spend = repository.createTransaction(
            TransactionIntent(
                title = "Groceries",
                date = LocalDate(2026, 3, 5),
                legs = listOf(TransactionLeg(type = TransactionType.EXPENSE, amount = 10.0, account = payer)),
            )
        )
        // Close the EXPENSE bucket the writer synthesized for the uncategorized leg.
        val expenseAccountId = db.entryDao().getEntriesWithAccountByTransactionId(spend.id)
            .first { it.account.type == AccountEntity.Type.EXPENSE }
            .account.id
        db.accountDao().close(expenseAccountId)

        repository.deleteTransactionById(spend.id)
        assertEquals(null, repository.getTransactionById(spend.id))
    }

    @Test
    fun `a paid invoice cannot have a purchase edited off it`() = runTest {
        val repository = repository(Invoice.Status.OPEN)
        val purchase = repository.createTransaction(purchase())

        // The invoice is settled; the edit points the leg somewhere else entirely.
        // Only the new side used to be checked, so the rewrite silently removed the
        // entries from the paid invoice.
        val locked = repository(Invoice.Status.PAID)

        assertFailsWith<InvoiceLockedException> {
            locked.updateTransaction(
                id = purchase.id,
                title = "Moved",
                date = LocalDate(2026, 3, 6),
                leg = TransactionLeg(type = TransactionType.EXPENSE, amount = 50.0, account = payer),
            )
        }
        assertEquals(2, locked.getTransactionById(purchase.id)?.entries?.size)
    }

    @Test
    fun `a closed account refuses new movement`() = runTest {
        val repository = repository(Invoice.Status.OPEN)
        db.accountDao().close(1)

        val error = assertFailsWith<ClosedAccountException> {
            repository.createTransaction(
                TransactionIntent(
                    title = "Groceries",
                    date = LocalDate(2026, 3, 5),
                    legs = listOf(TransactionLeg(type = TransactionType.EXPENSE, amount = 10.0, account = payer)),
                )
            )
        }
        assertEquals(LedgerError.ClosedAccount(ClosedFacade.ACCOUNT), error.error)
    }

    @Test
    fun `a paid invoice refuses even its own payment`() = runTest {
        val error = assertFailsWith<InvoiceLockedException> {
            repository(Invoice.Status.PAID).createTransaction(payment())
        }
        assertEquals(LedgerError.PaidInvoice, error.error)
    }
}

/**
 * Reads the accounts back from the ledger, so entries created on accounts the
 * writer synthesized (the card's, the uncategorized bucket) still hydrate.
 */
private class LedgerAccountRepository(private val db: AppDatabase) : IAccountRepository {
    override suspend fun getAllAccounts(): List<Account> = db.accountDao().getAllLedgerAccounts().map {
        Account(
            id = it.id,
            name = it.name,
            type = AccountType.valueOf(it.type.name),
            currency = it.currency,
            iconKey = it.iconKey,
            isDefault = it.isDefault,
            createdAt = it.createdAt,
            isArchived = it.isArchived,
        )
    }

    override fun observeAllAccounts(): Flow<List<Account>> = flowOf(emptyList())
    override suspend fun getAllAccountsIncludingClosed(): List<Account> = getAllAccounts()

    override fun observeAllAccountsIncludingClosed(): Flow<List<Account>> = observeAllAccounts()

    override suspend fun getAllLedgerAccounts(): List<Account> = getAllAccounts()
    override fun observeAllLedgerAccounts(): Flow<List<Account>> = flowOf(emptyList())
    override suspend fun getAccountById(accountId: Long): Account? = throw NotImplementedError()
    override fun observeAccountById(accountId: Long): Flow<Account?> = throw NotImplementedError()
    override suspend fun getDefaultAccount(): Account? = throw NotImplementedError()
    override fun observeDefaultAccount(): Flow<Account?> = throw NotImplementedError()
    override suspend fun getAccountCount(): Int = throw NotImplementedError()
    override suspend fun insert(account: Account): Long = throw NotImplementedError()
    override suspend fun update(account: Account) = throw NotImplementedError()
    override suspend fun delete(account: Account) = throw NotImplementedError()
}

private class SingleInvoiceRepository(private val invoice: Invoice) : IInvoiceRepository {
    override suspend fun getAllInvoices(): List<Invoice> = listOf(invoice)
    override fun observeAllInvoices(): Flow<List<Invoice>> = flowOf(listOf(invoice))
    override suspend fun getInvoiceById(id: Long): Invoice? = invoice.takeIf { it.id == id }
    override fun observeInvoicesByCreditCard(creditCardId: Long): Flow<List<Invoice>> = throw NotImplementedError()
    override fun observeInvoiceById(invoiceId: Long): Flow<Invoice?> = throw NotImplementedError()
    override fun observeOpenInvoice(creditCardId: Long): Flow<Invoice?> = throw NotImplementedError()
    override fun observeAvailableInvoices(creditCardId: Long): Flow<List<Invoice>> = throw NotImplementedError()
    override fun observeUnpaidInvoice(creditCardId: Long): Flow<Invoice?> = throw NotImplementedError()
    override fun observeUnpaidInvoices(): Flow<List<Invoice>> = throw NotImplementedError()
    override suspend fun getInvoicesByCreditCard(creditCardId: Long): List<Invoice> = throw NotImplementedError()
    override suspend fun getUnpaidInvoicesByCreditCard(creditCardId: Long): List<Invoice> = throw NotImplementedError()
    override suspend fun getOpenInvoice(creditCardId: Long): Invoice? = throw NotImplementedError()
    override suspend fun insert(invoice: Invoice): Long = throw NotImplementedError()
    override suspend fun update(invoice: Invoice) = throw NotImplementedError()
    override suspend fun deleteById(id: Long) = throw NotImplementedError()
}
