package com.neoutils.finsight.database.repository

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.neoutils.finsight.database.AppDatabase
import com.neoutils.finsight.database.entity.TransactionEntity
import com.neoutils.finsight.database.mapper.RecurringMapper
import com.neoutils.finsight.database.mapper.RecurringOccurrenceMapper
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.ContraLeg
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.model.RecurringOccurrence
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionIntent
import com.neoutils.finsight.domain.model.TransactionLeg
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.repository.ITransactionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Creating a recurring out of a transaction writes three rows — the template, the
 * transaction and the occurrence that records it as cycle 1 — and they have to persist
 * together or not at all, against a real database.
 *
 * A template left behind by a refused transaction is worse than no template: it would
 * be offered as a pending cycle moments after the screen said the write failed.
 *
 * This is also the only test that exercises the three nested writer connections in one
 * coroutine. A dispatcher switch anywhere along the path would deadlock here instead of
 * emitting a `SAVEPOINT`, which is why the whole path is run and not just its pieces.
 */
class CreateWithFirstCycleAtomicityTest {

    private val db = Room.inMemoryDatabaseBuilder<AppDatabase>()
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()

    @AfterTest
    fun tearDown() = db.close()

    private val date = LocalDate(2026, 7, 5)
    private val yearMonth = YearMonth(2026, 7)

    /**
     * Writes a real `transactions` row, the way the ledger's repository would, and
     * nothing else — what is under test is the enclosing unit of work, not the
     * balancing the write boundary does.
     */
    private class RecordingTransactionRepository(
        private val db: AppDatabase,
        private val failure: Throwable? = null,
    ) : ITransactionRepository {
        override suspend fun createTransaction(intent: TransactionIntent): Transaction {
            failure?.let { throw it }
            val id = db.transactionDao().insert(
                TransactionEntity(
                    title = intent.title,
                    date = intent.date,
                    recurringId = intent.recurringId,
                    recurringCycle = intent.recurringCycle,
                )
            )
            return Transaction(id = id, title = intent.title, date = intent.date)
        }

        override fun observeAllTransactions(): Flow<List<Transaction>> = throw NotImplementedError()
        override fun observeTransactionsBy(date: LocalDate?, dimensionId: Long?, accountId: Long?): Flow<List<Transaction>> = throw NotImplementedError()
        override fun observeTransactionById(id: Long): Flow<Transaction?> = throw NotImplementedError()
        override suspend fun getAllTransactions(): List<Transaction> = throw NotImplementedError()
        override suspend fun getTransactionsBetween(
            startDate: LocalDate,
            endDate: LocalDate,
        ): List<Transaction> = throw NotImplementedError()

        override suspend fun getTransactionsByIds(ids: Collection<Long>): List<Transaction> =
            throw NotImplementedError()
        override suspend fun getTransactionById(id: Long): Transaction? = throw NotImplementedError()
        override suspend fun getExistingTransactionIds(ids: Collection<Long>): Set<Long> = throw NotImplementedError()
        override suspend fun createTransactions(intents: List<TransactionIntent>): List<Transaction> = throw NotImplementedError()
        override suspend fun updateTransaction(id: Long, title: String?, date: LocalDate, legs: List<TransactionLeg>, contra: ContraLeg?) = throw NotImplementedError()
        override suspend fun deleteTransactionById(id: Long) = throw NotImplementedError()
        override suspend fun deleteTransactionsByIds(ids: List<Long>) = throw NotImplementedError()
    }

    // The lookups only hydrate reads; creating a cycle never consults them. Three
    // objects and not one: `unarchive` collides between categories and cards.
    private object NoCategories : ICategoryRepository {
        override fun observeAllCategories(): Flow<List<Category>> = throw NotImplementedError()
        override fun observeAllCategoriesIncludingClosed(): Flow<List<Category>> = throw NotImplementedError()
        override fun observeCategoriesByType(type: Category.Type): Flow<List<Category>> = throw NotImplementedError()
        override fun observeCategoryById(id: Long): Flow<Category?> = throw NotImplementedError()
        override suspend fun getAllCategories(): List<Category> = throw NotImplementedError()
        override suspend fun getAllCategoriesIncludingClosed(): List<Category> = throw NotImplementedError()
        override suspend fun getCategoryById(id: Long): Category? = throw NotImplementedError()
        override suspend fun getCategoryBySystemKey(systemKey: String): Category? = throw NotImplementedError()
        override suspend fun getCategoryByDimensionId(dimensionId: Long): Category? = throw NotImplementedError()
        override suspend fun archive(id: Long) = throw NotImplementedError()
        override suspend fun unarchive(id: Long) = throw NotImplementedError()
        override suspend fun existsByName(name: String, ignoreId: Long): Boolean = throw NotImplementedError()
        override suspend fun insert(category: Category) = throw NotImplementedError()
        override suspend fun insertAll(categories: List<Category>) = throw NotImplementedError()
        override suspend fun update(category: Category) = throw NotImplementedError()
        override suspend fun delete(category: Category) = throw NotImplementedError()
    }

    private object NoAccounts : IAccountRepository {
        override fun observeAllAccounts(): Flow<List<Account>> = throw NotImplementedError()
        override fun observeAllAccountsIncludingClosed(): Flow<List<Account>> = throw NotImplementedError()
        override fun observeAllLedgerAccounts(): Flow<List<Account>> = throw NotImplementedError()
        override fun observeAccountById(accountId: Long): Flow<Account?> = throw NotImplementedError()
        override fun observeDefaultAccount(): Flow<Account?> = throw NotImplementedError()
        override suspend fun getAllAccounts(): List<Account> = throw NotImplementedError()
        override suspend fun getAllAccountsIncludingClosed(): List<Account> = throw NotImplementedError()
        override suspend fun getAllLedgerAccounts(): List<Account> = throw NotImplementedError()
        override suspend fun getAccountById(accountId: Long): Account? = throw NotImplementedError()
        override suspend fun getDefaultAccount(): Account? = throw NotImplementedError()
        override suspend fun hasYieldingAccount(): Boolean = throw NotImplementedError()
        override suspend fun getAccountCount(): Int = throw NotImplementedError()
        override suspend fun insert(account: Account): Long = throw NotImplementedError()
        override suspend fun update(account: Account) = throw NotImplementedError()
        override suspend fun delete(account: Account) = throw NotImplementedError()
        override suspend fun reopen(accountId: Long) = throw NotImplementedError()
    }

    private object NoCards : ICreditCardRepository {
        override fun observeAllCreditCards(): Flow<List<CreditCard>> = throw NotImplementedError()
        override fun observeAllCreditCardsIncludingClosed(): Flow<List<CreditCard>> = throw NotImplementedError()
        override fun observeCreditCardById(creditCardId: Long): Flow<CreditCard?> = throw NotImplementedError()
        override suspend fun getAllCreditCards(): List<CreditCard> = throw NotImplementedError()
        override suspend fun getAllCreditCardsIncludingClosed(): List<CreditCard> = throw NotImplementedError()
        override suspend fun getCreditCardById(creditCardId: Long): CreditCard? = throw NotImplementedError()
        override suspend fun insert(creditCard: CreditCard, currency: String): Long = throw NotImplementedError()
        override suspend fun update(creditCard: CreditCard) = throw NotImplementedError()
        override suspend fun delete(creditCard: CreditCard) = throw NotImplementedError()
        override suspend fun unarchive(accountId: Long) = throw NotImplementedError()
        override suspend fun currencyForNewCard(): String = throw NotImplementedError()
    }

    private fun repository(failure: Throwable? = null) = RecurringRepository(
        database = db,
        dao = db.recurringDao(),
        mapper = RecurringMapper(),
        categoryRepository = NoCategories,
        accountRepository = NoAccounts,
        creditCardRepository = NoCards,
        occurrenceRepository = RecurringOccurrenceRepository(
            database = db,
            dao = db.recurringOccurrenceDao(),
            mapper = RecurringOccurrenceMapper(),
            transactionRepository = RecordingTransactionRepository(db, failure),
        ),
    )

    private val recurring = Recurring(
        type = TransactionType.EXPENSE,
        amount = 100.0,
        title = "Rent",
        dayOfMonth = 5,
        category = null,
        account = null,
        creditCard = null,
        createdAt = 0L,
    )

    private val intent = TransactionIntent(
        title = "Rent",
        date = date,
        recurringCycle = 1,
        legs = emptyList(),
        contra = null,
    )

    private val occurrence = RecurringOccurrence(
        recurringId = 0L,
        cycleNumber = 1,
        yearMonth = yearMonth,
        status = RecurringOccurrence.Status.CONFIRMED,
        effectiveDate = date,
        handledAt = 0L,
    )

    @Test
    fun `the three rows persist together, linked by the id the insert produced`() = runTest {
        val transaction = repository().createWithFirstCycle(recurring, intent, occurrence)

        val template = db.recurringDao().getAll().single()
        val written = assertNotNull(db.transactionDao().getById(transaction.id))
        val saved = assertNotNull(
            db.recurringOccurrenceDao().getByRecurringAndMonth(template.id, yearMonth)
        )

        assertEquals(template.id, written.recurringId)
        assertEquals(1, written.recurringCycle)
        assertEquals(transaction.id, saved.transactionId)
        assertEquals(1, saved.cycleNumber)
    }

    @Test
    fun `a refused transaction leaves no template behind`() = runTest {
        val repository = repository(failure = IllegalStateException("closed invoice"))

        assertFailsWith<IllegalStateException> {
            repository.createWithFirstCycle(recurring, intent, occurrence)
        }

        assertTrue(db.recurringDao().getAll().isEmpty())
        assertEquals(emptyList(), db.transactionDao().getAll())
    }
}
