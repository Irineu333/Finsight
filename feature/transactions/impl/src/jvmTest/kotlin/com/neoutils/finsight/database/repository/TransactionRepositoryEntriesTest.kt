package com.neoutils.finsight.database.repository

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.neoutils.finsight.database.AppDatabase
import com.neoutils.finsight.database.entity.AccountEntity
import com.neoutils.finsight.database.entity.EntryEntity
import com.neoutils.finsight.database.entity.TransactionEntity
import com.neoutils.finsight.database.mapper.TransactionMapper
import com.neoutils.finsight.database.mapper.RecurringMapper
import com.neoutils.finsight.domain.model.Account
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
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

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

    private fun repository(accounts: List<Account>) = TransactionRepository(
        database = db,
        transactionDao = db.transactionDao(),
        entryDao = db.entryDao(),
        recurringDao = db.recurringDao(),
        categoryRepository = FakeCategoryRepository,
        creditCardRepository = FakeCreditCardRepository,
        invoiceRepository = FakeInvoiceRepository,
        installmentRepository = FakeInstallmentRepository,
        accountRepository = FakeAccountRepository(accounts),
        transactionMapper = TransactionMapper(),
        recurringMapper = RecurringMapper(),
        ledgerEntryWriter = LedgerEntryWriter(db.entryDao(), db.accountDao(), db.categoryDao(), db.creditCardDao()),
    )

    @Test
    fun `getTransactionById hydrates the transaction's ledger entries`() = runTest {
        val asset = Account(id = 1, name = "A", type = AccountType.ASSET)
        val expense = Account(id = 10, name = "Food", type = AccountType.EXPENSE)
        db.accountDao().insert(AccountEntity(id = 1, name = "A", type = AccountEntity.Type.ASSET))
        db.accountDao().insert(AccountEntity(id = 10, name = "Food", type = AccountEntity.Type.EXPENSE))

        val transactionId = db.transactionDao().insert(
            TransactionEntity(title = "Groceries", date = LocalDate(2026, 3, 10), categoryId = null),
        )
        db.entryDao().insertAll(
            listOf(
                EntryEntity(transactionId = transactionId, accountId = 1, amount = -5000),
                EntryEntity(transactionId = transactionId, accountId = 10, amount = 5000),
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
        val card = Account(id = 2, name = "Card", type = AccountType.LIABILITY)
        val food = Account(id = 10, name = "Food", type = AccountType.EXPENSE)
        db.accountDao().insert(AccountEntity(id = 2, name = "Card", type = AccountEntity.Type.LIABILITY))
        db.accountDao().insert(AccountEntity(id = 10, name = "Food", type = AccountEntity.Type.EXPENSE))

        val transactionId = db.transactionDao().insert(
            TransactionEntity(title = "Groceries", date = LocalDate(2026, 3, 10), categoryId = null),
        )
        db.entryDao().insertAll(
            listOf(
                EntryEntity(transactionId = transactionId, accountId = 2, amount = -5000),
                EntryEntity(transactionId = transactionId, accountId = 10, amount = 5000),
            ),
        )

        val transaction = repository(listOf(card, food)).getTransactionById(transactionId)

        assertEquals(2, transaction?.entries?.size)
        assertEquals(setOf(2L, 10L), transaction?.entries?.map { it.account.id }?.toSet())
    }

    @Test
    fun `a closed account still hydrates the history that references it`() = runTest {
        // Closure hides an account from the selectors, not from the past.
        val closed = Account(id = 1, name = "Old wallet", type = AccountType.ASSET, isClosed = true)
        val food = Account(id = 10, name = "Food", type = AccountType.EXPENSE)
        db.accountDao().insert(
            AccountEntity(id = 1, name = "Old wallet", type = AccountEntity.Type.ASSET, isClosed = true),
        )
        db.accountDao().insert(AccountEntity(id = 10, name = "Food", type = AccountEntity.Type.EXPENSE))

        val transactionId = db.transactionDao().insert(
            TransactionEntity(title = "Old", date = LocalDate(2026, 1, 5), categoryId = null),
        )
        db.entryDao().insertAll(
            listOf(
                EntryEntity(transactionId = transactionId, accountId = 1, amount = -1000),
                EntryEntity(transactionId = transactionId, accountId = 10, amount = 1000),
            ),
        )

        val transaction = repository(listOf(closed, food)).getTransactionById(transactionId)

        assertEquals(2, transaction?.entries?.size)
        assertEquals(true, transaction?.entries?.first { it.account.id == 1L }?.account?.isClosed)
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
    override suspend fun insert(category: Category) = throw NotImplementedError()
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
    override suspend fun insert(creditCard: CreditCard): Long = throw NotImplementedError()
    override suspend fun update(creditCard: CreditCard) = throw NotImplementedError()
    override suspend fun delete(creditCard: CreditCard) = throw NotImplementedError()
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
    override suspend fun getInvoicesByCreditCard(creditCardId: Long): List<Invoice> = throw NotImplementedError()
    override suspend fun getUnpaidInvoicesByCreditCard(creditCardId: Long): List<Invoice> = throw NotImplementedError()
    override suspend fun getOpenInvoice(creditCardId: Long): Invoice? = throw NotImplementedError()
    override suspend fun getInvoiceById(id: Long): Invoice? = throw NotImplementedError()
    override suspend fun insert(invoice: Invoice): Long = throw NotImplementedError()
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
    private val facade = accounts.filter { it.type == AccountType.ASSET && !it.isClosed }
    override suspend fun getAllAccounts(): List<Account> = facade
    override fun observeAllAccounts(): Flow<List<Account>> = flowOf(facade)
    override suspend fun getAllLedgerAccounts(): List<Account> = accounts
    override fun observeAllLedgerAccounts(): Flow<List<Account>> = flowOf(accounts)
    override suspend fun getAccountById(accountId: Long): Account? = throw NotImplementedError()
    override fun observeAccountById(accountId: Long): Flow<Account?> = throw NotImplementedError()
    override suspend fun getDefaultAccount(): Account? = throw NotImplementedError()
    override fun observeDefaultAccount(): Flow<Account?> = throw NotImplementedError()
    override suspend fun getAccountCount(): Int = throw NotImplementedError()
    override suspend fun insert(account: Account): Long = throw NotImplementedError()
    override suspend fun update(account: Account) = throw NotImplementedError()
    override suspend fun delete(account: Account) = throw NotImplementedError()
}
