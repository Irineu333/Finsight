package com.neoutils.finsight.database.repository

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.neoutils.finsight.database.AppDatabase
import com.neoutils.finsight.database.entity.RecurringEntity
import com.neoutils.finsight.database.entity.TransactionEntity
import com.neoutils.finsight.database.mapper.RecurringOccurrenceMapper
import com.neoutils.finsight.domain.model.RecurringOccurrence
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionIntent
import com.neoutils.finsight.domain.repository.ITransactionRepository
import com.neoutils.finsight.domain.model.ContraLeg
import com.neoutils.finsight.domain.model.TransactionLeg
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
import kotlin.test.assertNull

/**
 * Confirming a cycle writes two rows — the transaction and the occurrence that records
 * it — and they have to persist together or not at all, against a real database.
 *
 * The gap between them is what made a duplicated ledger entry reachable: a transaction
 * without its occurrence puts the month back in the pending list, and the re-entry
 * check finds no occurrence to refuse.
 */
class ConfirmCycleAtomicityTest {

    private val db = Room.inMemoryDatabaseBuilder<AppDatabase>()
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()

    @AfterTest
    fun tearDown() = db.close()

    /**
     * Writes a real `transactions` row, the way the ledger's repository would, and
     * nothing else — the point under test is the enclosing unit of work, not the
     * balancing the write boundary does.
     */
    private class RecordingTransactionRepository(
        private val db: AppDatabase,
    ) : ITransactionRepository {
        override suspend fun createTransaction(intent: TransactionIntent): Transaction {
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
        override suspend fun getTransactionById(id: Long): Transaction? = throw NotImplementedError()
        override suspend fun getExistingTransactionIds(ids: Collection<Long>): Set<Long> = throw NotImplementedError()
        override suspend fun createTransactions(intents: List<TransactionIntent>): List<Transaction> = throw NotImplementedError()
        override suspend fun updateTransaction(id: Long, title: String?, date: LocalDate, leg: TransactionLeg, contra: ContraLeg?) = throw NotImplementedError()
        override suspend fun deleteTransactionById(id: Long) = throw NotImplementedError()
        override suspend fun deleteTransactionsByIds(ids: List<Long>) = throw NotImplementedError()
    }

    private fun repository(
        transactionRepository: ITransactionRepository = RecordingTransactionRepository(db),
    ) = RecurringOccurrenceRepository(
        database = db,
        dao = db.recurringOccurrenceDao(),
        mapper = RecurringOccurrenceMapper(),
        transactionRepository = transactionRepository,
    )

    private val yearMonth = YearMonth(2026, 7)
    private val date = LocalDate(2026, 7, 5)

    private val intent = TransactionIntent(
        title = "Rent",
        date = date,
        recurringId = 1L,
        recurringCycle = 1,
        legs = emptyList(),
        contra = null,
    )

    /** `recurring_occurrences.recurringId` is a real foreign key: the template must exist. */
    private suspend fun givenRecurring() {
        db.recurringDao().insert(
            RecurringEntity(
                id = 1L,
                type = RecurringEntity.Type.EXPENSE,
                amount = 100.0,
                title = "Rent",
                dayOfMonth = 5,
                categoryId = null,
                accountId = null,
                creditCardId = null,
                createdAt = 0L,
            )
        )
    }

    private fun occurrence() = RecurringOccurrence(
        recurringId = 1L,
        cycleNumber = 1,
        yearMonth = yearMonth,
        status = RecurringOccurrence.Status.CONFIRMED,
        effectiveDate = date,
        handledAt = 0L,
    )

    @Test
    fun `both rows persist on success`() = runTest {
        givenRecurring()
        val transaction = repository().confirmCycle(intent, occurrence())

        assertNotNull(db.transactionDao().getById(transaction.id))
        val saved = db.recurringOccurrenceDao().getByRecurringAndMonth(1L, yearMonth)
        assertEquals(transaction.id, assertNotNull(saved).transactionId)
    }

    @Test
    fun `a failure recording the occurrence takes the transaction with it`() = runTest {
        // Pointing the occurrence at a template that does not exist makes its foreign
        // key fail — the second write blowing up after the first succeeded, which is
        // exactly the crack the old two-transaction version left open.
        givenRecurring()
        val repository = repository()

        assertFailsWith<Throwable> {
            repository.confirmCycle(intent, occurrence().copy(recurringId = -1L))
        }

        assertEquals(emptyList(), db.transactionDao().getAll())
        assertNull(db.recurringOccurrenceDao().getByRecurringAndMonth(-1L, yearMonth))
    }

    @Test
    fun `confirming the same cycle twice writes no second entry`() = runTest {
        givenRecurring()
        val repository = repository()
        repository.confirmCycle(intent, occurrence())

        assertFailsWith<IllegalArgumentException> {
            repository.confirmCycle(intent, occurrence())
        }

        // The re-entry check lives inside the transaction, so the refusal happens
        // before the ledger is touched — one row, not two.
        assertEquals(1, db.transactionDao().getAll().size)
    }
}
