package com.neoutils.finsight.database.repository

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.neoutils.finsight.database.AppDatabase
import com.neoutils.finsight.database.entity.AccountEntity
import com.neoutils.finsight.database.entity.EntryEntity
import com.neoutils.finsight.database.entity.TransactionEntity
import com.neoutils.finsight.database.mapper.TransactionMapper
import com.neoutils.finsight.database.mapper.RecurringMapper
import com.neoutils.finsight.domain.ledger.DimensionWriteGuard
import com.neoutils.finsight.domain.ledger.LedgerWrite
import com.neoutils.finsight.domain.ledger.TransactionRemovalHook
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.domain.model.TransactionLeg
import com.neoutils.finsight.domain.model.TransactionIntent
import com.neoutils.finsight.domain.model.DimensionKind
import com.neoutils.finsight.domain.model.ContraLeg
import com.neoutils.finsight.database.entity.DimensionEntity
import com.neoutils.finsight.domain.error.UnbalancedTransactionException
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Installment
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.repository.IInstallmentRepository
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
import kotlin.test.assertTrue

/**
 * End-to-end proof of task 2.7 against a real in-memory Room database: the
 * [TransactionRepository] read path (`getTransactionById`) populates
 * [com.neoutils.finsight.domain.model.Transaction.entries] with the transaction's ledger
 * legs, each hydrated with its account, from real `entries`/`accounts` rows.
 */
class TransactionRepositoryEntriesTest {

    private val db = Room.inMemoryDatabaseBuilder<AppDatabase>()
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()

    @AfterTest fun tearDown() = db.close()

    private fun repository(
        accounts: List<Account>,
        writeGuard: DimensionWriteGuard = DimensionWriteGuard.None,
    ) = TransactionRepository(
        database = db,
        transactionDao = db.transactionDao(),
        entryDao = db.entryDao(),
        accountDao = db.accountDao(),
        writeGuard = writeGuard,
        removalHook = TransactionRemovalHook.None,
        transactionMapper = TransactionMapper(),
        ledgerEntryWriter = LedgerEntryWriter(db.entryDao(), db.accountDao(), db.dimensionDao()),
    )

    private suspend fun asset(id: Long, name: String, currency: String): Account {
        db.accountDao().insert(
            AccountEntity(id = id, name = name, type = AccountEntity.Type.ASSET, currency = currency),
        )
        return Account(id = id, name = name, type = AccountType.ASSET, currency = currency)
    }

    @Test
    fun `getTransactionById hydrates the transaction's ledger entries`() = runTest {
        val asset = Account(id = 1, name = "A", type = AccountType.ASSET, currency = "BRL")
        val expense = Account(id = 10, name = "Food", type = AccountType.EXPENSE, currency = "BRL")
        db.accountDao().insert(AccountEntity(id = 1, name = "A", type = AccountEntity.Type.ASSET, currency = "BRL"))
        db.accountDao().insert(AccountEntity(id = 10, name = "Food", type = AccountEntity.Type.EXPENSE, currency = "BRL"))

        val transactionId = db.transactionDao().insert(
            TransactionEntity(title = "Groceries", date = LocalDate(2026, 3, 10)),
        )
        db.entryDao().insertAll(
            listOf(
                EntryEntity(transactionId = transactionId, accountId = 1, amount = -5000, currency = "BRL"),
                EntryEntity(transactionId = transactionId, accountId = 10, amount = 5000, currency = "BRL"),
            ),
        )

        val transaction = repository(listOf(asset, expense)).getTransactionById(transactionId)!!

        assertEquals(2, transaction.entries.size, "both ledger legs are carried onto the transaction")
        val assetLeg = transaction.entries.first { it.account.id == 1L }
        val expenseLeg = transaction.entries.first { it.account.id == 10L }
        assertEquals(-5000, assetLeg.amount)
        assertEquals(AccountType.ASSET, assetLeg.account.type)
        assertEquals(5000, expenseLeg.amount)
        assertEquals(AccountType.EXPENSE, expenseLeg.account.type)
        assertEquals(transactionId, assetLeg.transactionId)
    }

    @Test
    fun `a card purchase hydrates even though it has no asset leg`() = runTest {
        // LIABILITY (the card) + EXPENSE (the category). Neither is an account the
        // user sees, so hydrating from the ASSET facade dropped both entries, the
        // mapper returned null, and `createTransaction` threw on `!!` — the modal
        // simply never closed, while the invoice grew from the rows already written.
        val card = Account(id = 2, name = "Card", type = AccountType.LIABILITY, currency = "BRL")
        val food = Account(id = 10, name = "Food", type = AccountType.EXPENSE, currency = "BRL")
        db.accountDao().insert(AccountEntity(id = 2, name = "Card", type = AccountEntity.Type.LIABILITY, currency = "BRL"))
        db.accountDao().insert(AccountEntity(id = 10, name = "Food", type = AccountEntity.Type.EXPENSE, currency = "BRL"))

        val transactionId = db.transactionDao().insert(
            TransactionEntity(title = "Groceries", date = LocalDate(2026, 3, 10)),
        )
        db.entryDao().insertAll(
            listOf(
                EntryEntity(transactionId = transactionId, accountId = 2, amount = -5000, currency = "BRL"),
                EntryEntity(transactionId = transactionId, accountId = 10, amount = 5000, currency = "BRL"),
            ),
        )

        val transaction = repository(listOf(card, food)).getTransactionById(transactionId)

        assertEquals(2, transaction?.entries?.size)
        assertEquals(setOf(2L, 10L), transaction?.entries?.map { it.account.id }?.toSet())
    }

    @Test
    fun `a closed account still hydrates the history that references it`() = runTest {
        // Closure hides an account from the selectors, not from the past.
        val closed = Account(id = 1, name = "Old wallet", type = AccountType.ASSET, isArchived = true, currency = "BRL")
        val food = Account(id = 10, name = "Food", type = AccountType.EXPENSE, currency = "BRL")
        db.accountDao().insert(
            AccountEntity(id = 1, name = "Old wallet", type = AccountEntity.Type.ASSET, isArchived = true, currency = "BRL"),
        )
        db.accountDao().insert(AccountEntity(id = 10, name = "Food", type = AccountEntity.Type.EXPENSE, currency = "BRL"))

        val transactionId = db.transactionDao().insert(
            TransactionEntity(title = "Old", date = LocalDate(2026, 1, 5)),
        )
        db.entryDao().insertAll(
            listOf(
                EntryEntity(transactionId = transactionId, accountId = 1, amount = -1000, currency = "BRL"),
                EntryEntity(transactionId = transactionId, accountId = 10, amount = 1000, currency = "BRL"),
            ),
        )

        val transaction = repository(listOf(closed, food)).getTransactionById(transactionId)

        assertEquals(2, transaction?.entries?.size)
        assertEquals(true, transaction?.entries?.first { it.account.id == 1L }?.account?.isArchived)
    }

    @Test
    fun `editing a transaction rewrites both legs, keeping it balanced and classified`() = runTest {
        // The rewrite deletes the old entries and writes new ones, so an edit that
        // carries only the money leg leaves the transaction one-sided — refused at
        // the boundary, and the whole edit rolled back with it. That is what a
        // nullable `contra` with a default let a caller do by omission.
        val asset = Account(id = 1, name = "A", type = AccountType.ASSET, currency = "BRL")
        val nominal = Account(id = 10, name = "Despesas", type = AccountType.EXPENSE, currency = "BRL")
        db.accountDao().insert(AccountEntity(id = 1, name = "A", type = AccountEntity.Type.ASSET, currency = "BRL"))
        db.accountDao().insert(AccountEntity(id = 10, name = "Despesas", type = AccountEntity.Type.EXPENSE, currency = "BRL"))
        db.dimensionDao().insert(DimensionEntity(id = 7, kind = DimensionKind.CATEGORY))
        val repository = repository(listOf(asset, nominal))

        val created = repository.createTransaction(
            TransactionIntent(
                title = "Groceries",
                date = LocalDate(2026, 3, 10),
                legs = listOf(TransactionLeg(type = TransactionType.EXPENSE, amount = 50.0, accountId = 1)),
                contra = ContraLeg(AccountType.EXPENSE, dimensionId = 7),
            )
        )

        repository.updateTransaction(
            id = created.id,
            title = "Groceries, corrected",
            date = LocalDate(2026, 3, 11),
            legs = listOf(TransactionLeg(type = TransactionType.EXPENSE, amount = 80.0, accountId = 1)),
            contra = ContraLeg(AccountType.EXPENSE, dimensionId = 7),
        )

        val edited = repository.getTransactionById(created.id)!!
        assertEquals("Groceries, corrected", edited.title)
        assertEquals(2, edited.entries.size, "the edit must not leave the transaction one-sided")
        assertEquals(0L, edited.entries.sumOf { it.amount }, "and it must still balance")
        assertEquals(-8000L, edited.entries.first { it.account.id == 1L }.amount)
        // The classification survives the rewrite: it travels in the contra leg now.
        assertEquals(7L, edited.entries.first { it.account.id == 10L }.dimensionId)
    }

    @Test
    fun `rewriting a two-legged operation replaces both legs and stays balanced`() = runTest {
        // The shape the single-leg signature made inexprimible: a transfer states both
        // ends, and the rewrite has to carry both. Stating one and letting the boundary
        // synthesize the other would move the money somewhere nobody chose.
        val source = asset(1, "Nubank", "BRL")
        val destination = asset(2, "Inter", "BRL")
        val repository = repository(listOf(source, destination))

        val created = repository.createTransaction(
            TransactionIntent(
                title = null,
                date = LocalDate(2026, 3, 10),
                legs = listOf(
                    TransactionLeg(type = TransactionType.EXPENSE, amount = 50.0, accountId = 1),
                    TransactionLeg(type = TransactionType.INCOME, amount = 50.0, accountId = 2),
                ),
            )
        )

        repository.updateTransaction(
            id = created.id,
            title = null,
            date = LocalDate(2026, 3, 11),
            legs = listOf(
                TransactionLeg(type = TransactionType.EXPENSE, amount = 80.0, accountId = 1),
                TransactionLeg(type = TransactionType.INCOME, amount = 80.0, accountId = 2),
            ),
            contra = null,
        )

        val edited = repository.getTransactionById(created.id)!!
        assertEquals(LocalDate(2026, 3, 11), edited.date)
        assertEquals(2, edited.entries.size, "the old legs are gone, not kept alongside the new")
        assertEquals(0L, edited.entries.sumOf { it.amount })
        assertEquals(-8000L, edited.entries.first { it.account.id == 1L }.amount)
        assertEquals(8000L, edited.entries.first { it.account.id == 2L }.amount)
    }

    @Test
    fun `rewriting across currencies rebuilds a conversion leg per currency`() = runTest {
        val source = asset(1, "Nubank", "BRL")
        val destination = asset(2, "Chase", "USD")
        val repository = repository(listOf(source, destination))

        val created = repository.createTransaction(
            TransactionIntent(
                title = null,
                date = LocalDate(2026, 3, 10),
                legs = listOf(
                    TransactionLeg(type = TransactionType.EXPENSE, amount = 550.0, accountId = 1),
                    TransactionLeg(type = TransactionType.INCOME, amount = 100.0, accountId = 2),
                ),
            )
        )
        assertEquals(4, created.entries.size)

        repository.updateTransaction(
            id = created.id,
            title = null,
            date = LocalDate(2026, 3, 10),
            legs = listOf(
                TransactionLeg(type = TransactionType.EXPENSE, amount = 600.0, accountId = 1),
                TransactionLeg(type = TransactionType.INCOME, amount = 110.0, accountId = 2),
            ),
            contra = null,
        )

        val edited = repository.getTransactionById(created.id)!!
        assertEquals(4, edited.entries.size, "two monetary legs and one conversion leg per currency")
        assertEquals(-60000L, edited.entries.first { it.account.id == 1L }.amount)
        assertEquals(11000L, edited.entries.first { it.account.id == 2L }.amount)

        val byCurrency = edited.entries.groupBy { it.currency }
        assertEquals(setOf("BRL", "USD"), byCurrency.keys)
        byCurrency.forEach { (currency, entries) ->
            assertEquals(0L, entries.sumOf { it.amount }, "$currency must sum to zero")
        }

        val conversion = edited.entries.filter { it.account.type == AccountType.CONVERSION }
        assertEquals(2, conversion.size)
        assertEquals(setOf("BRL", "USD"), conversion.map { it.currency }.toSet())
        assertEquals(60000L, conversion.first { it.currency == "BRL" }.amount)
        assertEquals(-11000L, conversion.first { it.currency == "USD" }.amount)
    }

    @Test
    fun `a rewrite tells the guard whether it settles a liability, deriving it from the legs`() = runTest {
        // The value used to be the literal `false`, whatever the legs said. It is now
        // derived from the natures of the accounts posted to — the same derivation
        // creation uses — so this write reports `true` where the constant reported
        // `false`. The transaction is created *without* a dimension so that the only
        // question the guard is ever asked is the one about the new legs.
        val payer = asset(1, "Nubank", "BRL")
        db.accountDao().insert(
            AccountEntity(id = 2, name = "Card", type = AccountEntity.Type.LIABILITY, currency = "BRL"),
        )
        db.dimensionDao().insert(DimensionEntity(id = 7, kind = DimensionKind.INVOICE))
        val card = Account(id = 2, name = "Card", type = AccountType.LIABILITY, currency = "BRL")

        val guard = RecordingWriteGuard()
        val repository = repository(listOf(payer, card), writeGuard = guard)

        val created = repository.createTransaction(
            TransactionIntent(
                title = null,
                date = LocalDate(2026, 3, 10),
                legs = listOf(
                    TransactionLeg(type = TransactionType.EXPENSE, amount = 50.0, accountId = 1),
                    TransactionLeg(type = TransactionType.INCOME, amount = 50.0, accountId = 2),
                ),
            )
        )
        guard.writes.clear()

        repository.updateTransaction(
            id = created.id,
            title = null,
            date = LocalDate(2026, 3, 10),
            legs = listOf(
                TransactionLeg(type = TransactionType.EXPENSE, amount = 60.0, accountId = 1),
                TransactionLeg(type = TransactionType.INCOME, amount = 60.0, accountId = 2, dimensionId = 7),
            ),
            contra = null,
        )

        assertEquals(1, guard.writes.size)
        assertEquals(setOf(7L), guard.writes.single().dimensionIds)
        assertEquals(true, guard.writes.single().settlesALiability)
    }

    @Test
    fun `a transfer rewrite reaches the guard with nothing to veto`() = runTest {
        // Two `ASSET` legs derive `settlesALiability = false`, which is what the
        // constant said too — so the transfer rewrite passes exactly as before. It
        // carries no dimension either (a category lands on a nominal leg, an invoice
        // on a liability one), so the guard is not consulted at all.
        val source = asset(1, "Nubank", "BRL")
        val destination = asset(2, "Inter", "BRL")
        val guard = RecordingWriteGuard()
        val repository = repository(listOf(source, destination), writeGuard = guard)

        val created = repository.createTransaction(
            TransactionIntent(
                title = null,
                date = LocalDate(2026, 3, 10),
                legs = listOf(
                    TransactionLeg(type = TransactionType.EXPENSE, amount = 50.0, accountId = 1),
                    TransactionLeg(type = TransactionType.INCOME, amount = 50.0, accountId = 2),
                ),
            )
        )
        guard.writes.clear()

        repository.updateTransaction(
            id = created.id,
            title = null,
            date = LocalDate(2026, 3, 10),
            legs = listOf(
                TransactionLeg(type = TransactionType.EXPENSE, amount = 80.0, accountId = 1),
                TransactionLeg(type = TransactionType.INCOME, amount = 80.0, accountId = 2),
            ),
            contra = null,
        )

        assertEquals(emptyList(), guard.writes)
        assertEquals(-8000L, repository.getTransactionById(created.id)!!.entries.first { it.account.id == 1L }.amount)
    }

    @Test
    fun `rewriting a crossing back into one currency leaves no conversion leg behind`() = runTest {
        // The completion is a property of the intent being written, not of the state
        // it replaces: point both ends at one currency and there is no residue, so
        // there is nothing for a conversion account to receive. The rewrite deletes
        // every old entry, so the conversion legs the crossing had are not amended
        // down to zero — they cease to exist.
        val source = asset(1, "Nubank", "BRL")
        val chase = asset(2, "Chase", "USD")
        val inter = asset(3, "Inter", "BRL")
        val repository = repository(listOf(source, chase, inter))

        val created = repository.createTransaction(
            TransactionIntent(
                title = null,
                date = LocalDate(2026, 3, 10),
                legs = listOf(
                    TransactionLeg(type = TransactionType.EXPENSE, amount = 550.0, accountId = 1),
                    TransactionLeg(type = TransactionType.INCOME, amount = 100.0, accountId = 2),
                ),
            )
        )
        assertEquals(4, created.entries.size)

        repository.updateTransaction(
            id = created.id,
            title = null,
            date = LocalDate(2026, 3, 10),
            legs = listOf(
                TransactionLeg(type = TransactionType.EXPENSE, amount = 550.0, accountId = 1),
                TransactionLeg(type = TransactionType.INCOME, amount = 550.0, accountId = 3),
            ),
            contra = null,
        )

        val edited = repository.getTransactionById(created.id)!!
        assertEquals(2, edited.entries.size, "only the two monetary legs are left")
        assertTrue(
            edited.entries.none { it.account.type == AccountType.CONVERSION },
            "a rewrite that crosses nothing has no residue to post",
        )
        assertEquals(setOf("BRL"), edited.entries.map { it.currency }.toSet())
        assertEquals(0L, edited.entries.sumOf { it.amount })
        assertEquals(-55000L, edited.entries.first { it.account.id == 1L }.amount)
        assertEquals(55000L, edited.entries.first { it.account.id == 3L }.amount)
    }

    @Test
    fun `a rewrite that fails after the old legs are gone leaves the operation as it was`() = runTest {
        // The row update and the ledger rewrite share one write transaction, and this
        // is the failure that would tell them apart: the legs are deleted first, and
        // the refusal comes from the boundary *after* that, with the new date already
        // written. Either the whole correction lands or none of it does — a
        // transaction stranded with no legs is not a state the ledger has a reading for.
        val source = asset(1, "Nubank", "BRL")
        val destination = asset(2, "Inter", "BRL")
        val repository = repository(listOf(source, destination))

        val created = repository.createTransaction(
            TransactionIntent(
                title = "Rent",
                date = LocalDate(2026, 3, 10),
                legs = listOf(
                    TransactionLeg(type = TransactionType.EXPENSE, amount = 50.0, accountId = 1),
                    TransactionLeg(type = TransactionType.INCOME, amount = 50.0, accountId = 2),
                ),
            )
        )

        assertFailsWith<UnbalancedTransactionException> {
            repository.updateTransaction(
                id = created.id,
                title = "Rent, corrected",
                date = LocalDate(2026, 3, 11),
                // One currency and a residue left over: refused inside the boundary,
                // which the old legs have already been deleted to reach.
                legs = listOf(
                    TransactionLeg(type = TransactionType.EXPENSE, amount = 80.0, accountId = 1),
                    TransactionLeg(type = TransactionType.INCOME, amount = 50.0, accountId = 2),
                ),
                contra = null,
            )
        }

        val untouched = repository.getTransactionById(created.id)!!
        assertEquals("Rent", untouched.title)
        assertEquals(LocalDate(2026, 3, 10), untouched.date)
        assertEquals(2, untouched.entries.size, "the legs it had are still the legs it has")
        assertEquals(-5000L, untouched.entries.first { it.account.id == 1L }.amount)
        assertEquals(5000L, untouched.entries.first { it.account.id == 2L }.amount)
    }

}

internal object FakeCategoryRepository : ICategoryRepository {
    override suspend fun getAllCategories(): List<Category> = emptyList()
    override suspend fun getAllCategoriesIncludingClosed(): List<Category> = getAllCategories()
    override fun observeAllCategoriesIncludingClosed(): Flow<List<Category>> = observeAllCategories()
    override fun observeAllCategories(): Flow<List<Category>> = flowOf(emptyList())
    override fun observeCategoryById(id: Long): Flow<Category?> = throw NotImplementedError()
    override fun observeCategoriesByType(type: Category.Type): Flow<List<Category>> = throw NotImplementedError()
    override suspend fun getCategoryById(id: Long): Category? = throw NotImplementedError()
    override suspend fun getCategoryBySystemKey(systemKey: String): Category? = null
    override suspend fun getCategoryByDimensionId(dimensionId: Long): Category? = null
    override suspend fun archive(id: Long) = Unit
    override suspend fun unarchive(id: Long) = Unit
    override suspend fun existsByName(name: String, ignoreId: Long): Boolean = false

    override suspend fun insert(category: Category) = throw NotImplementedError()
    override suspend fun insertAll(categories: List<Category>) = throw NotImplementedError()
    override suspend fun update(category: Category) = throw NotImplementedError()
    override suspend fun delete(category: Category) = throw NotImplementedError()
}

internal object FakeCreditCardRepository : ICreditCardRepository {
    override suspend fun getAllCreditCards(): List<CreditCard> = emptyList()
    override suspend fun getAllCreditCardsIncludingClosed(): List<CreditCard> = getAllCreditCards()
    override fun observeAllCreditCardsIncludingClosed(): Flow<List<CreditCard>> = observeAllCreditCards()
    override fun observeAllCreditCards(): Flow<List<CreditCard>> = flowOf(emptyList())
    override suspend fun getCreditCardById(creditCardId: Long): CreditCard? = throw NotImplementedError()
    override fun observeCreditCardById(creditCardId: Long): Flow<CreditCard?> = throw NotImplementedError()
    override suspend fun insert(creditCard: CreditCard, currency: String): Long = throw NotImplementedError()
    override suspend fun update(creditCard: CreditCard) = throw NotImplementedError()
    override suspend fun delete(creditCard: CreditCard) = throw NotImplementedError()
    override suspend fun unarchive(accountId: Long) = throw NotImplementedError()
    override suspend fun currencyForNewCard(): String = throw NotImplementedError()
}

private object FakeInvoiceRepository : IInvoiceRepository {
    override suspend fun getAllInvoices(): List<Invoice> = emptyList()
    override fun observeAllInvoices(): Flow<List<Invoice>> = flowOf(emptyList())
    override fun observeInvoicesByCreditCard(creditCardId: Long): Flow<List<Invoice>> = throw NotImplementedError()
    override fun observeInvoiceById(invoiceId: Long): Flow<Invoice?> = throw NotImplementedError()
    override fun observeOpenInvoice(creditCardId: Long): Flow<Invoice?> = throw NotImplementedError()
    override fun observeAvailableInvoices(creditCardId: Long): Flow<List<Invoice>> = throw NotImplementedError()
    override fun observeUnpaidInvoice(creditCardId: Long): Flow<Invoice?> = throw NotImplementedError()
    override fun observeUnpaidInvoices(): Flow<List<Invoice>> = throw NotImplementedError()
    override fun observeInvoicesToSettle(month: YearMonth): Flow<List<Invoice>> = throw NotImplementedError()
    override suspend fun getInvoicesByCreditCard(creditCardId: Long): List<Invoice> = throw NotImplementedError()
    override suspend fun getUnpaidInvoicesByCreditCard(creditCardId: Long): List<Invoice> = throw NotImplementedError()
    override suspend fun getOpenInvoice(creditCardId: Long): Invoice? = throw NotImplementedError()
    override suspend fun getInvoiceById(id: Long): Invoice? = throw NotImplementedError()
    override suspend fun insert(invoice: Invoice): Invoice = throw NotImplementedError()
    override suspend fun update(invoice: Invoice) = throw NotImplementedError()
    override suspend fun deleteById(id: Long) = throw NotImplementedError()
}

internal object FakeInstallmentRepository : IInstallmentRepository {
    override suspend fun getAllInstallments(): List<Installment> = emptyList()
    override fun observeAllInstallments(): Flow<List<Installment>> = flowOf(emptyList())
    override suspend fun getInstallmentById(id: Long): Installment? = throw NotImplementedError()
    override suspend fun createInstallment(count: Int, totalAmount: Double): Long = throw NotImplementedError()
    override suspend fun updateInstallment(id: Long, count: Int, totalAmount: Double) = throw NotImplementedError()
    override suspend fun deleteInstallmentById(id: Long) = throw NotImplementedError()

}

/**
 * Mirrors the real split: [getAllAccounts] is the user-facing facade and shows
 * only open `ASSET` rows, exactly like `AccountDao`; the ledger reads see the
 * whole chart. A fake returning everything from both would hide the very bug this
 * distinction exists to prevent.
 */
internal class FakeAccountRepository(private val accounts: List<Account>) : IAccountRepository {
    private val facade = accounts.filter { it.type == AccountType.ASSET && !it.isArchived }
    override suspend fun getAllAccounts(): List<Account> = facade
    override fun observeAllAccounts(): Flow<List<Account>> = flowOf(facade)
    override suspend fun getAllAccountsIncludingClosed(): List<Account> = facade
    override fun observeAllAccountsIncludingClosed(): Flow<List<Account>> = flowOf(facade)
    override suspend fun getAllLedgerAccounts(): List<Account> = accounts
    override fun observeAllLedgerAccounts(): Flow<List<Account>> = flowOf(accounts)
    override suspend fun getAccountById(accountId: Long): Account? = throw NotImplementedError()
    override fun observeAccountById(accountId: Long): Flow<Account?> = throw NotImplementedError()
    override suspend fun getDefaultAccount(): Account? = throw NotImplementedError()
    override fun observeDefaultAccount(): Flow<Account?> = throw NotImplementedError()
    override suspend fun hasYieldingAccount(): Boolean = false
    override suspend fun getAccountCount(): Int = throw NotImplementedError()
    override suspend fun insert(account: Account): Long = throw NotImplementedError()
    override suspend fun update(account: Account) = throw NotImplementedError()
    override suspend fun delete(account: Account) = throw NotImplementedError()
    override suspend fun reopen(accountId: Long) = throw NotImplementedError()

}

private class RecordingWriteGuard : DimensionWriteGuard {
    val writes = mutableListOf<LedgerWrite>()
    override suspend fun ensureAccepts(write: LedgerWrite) { writes += write }
}
