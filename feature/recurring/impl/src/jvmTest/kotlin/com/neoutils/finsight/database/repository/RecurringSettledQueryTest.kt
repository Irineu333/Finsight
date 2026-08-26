package com.neoutils.finsight.database.repository

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.neoutils.finsight.database.AppDatabase
import com.neoutils.finsight.database.entity.AccountEntity
import com.neoutils.finsight.database.entity.EntryEntity
import com.neoutils.finsight.database.entity.RecurringEntity
import com.neoutils.finsight.database.entity.RecurringOccurrenceEntity
import com.neoutils.finsight.database.mapper.RecurringOccurrenceMapper
import com.neoutils.finsight.database.entity.TransactionEntity
import com.neoutils.finsight.domain.model.MoneyByCurrency
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

/**
 * **What a month's confirmed cycles actually posted, read from the ledger against a real
 * database.**
 *
 * The half of the summary that is *fact* cannot be derived from the templates: confirming
 * a cycle lets the user override the amount and the template stays as it was. So the read
 * walks `recurring_occurrences → transactions → entries → accounts`, and these tests are
 * what say it walks it correctly — the nature comes off the nominal account, the currency
 * off the leg, and the month off the occurrence.
 */
class RecurringSettledQueryTest {

    private val db = Room.inMemoryDatabaseBuilder<AppDatabase>()
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()

    @AfterTest
    fun tearDown() = db.close()

    private val month = YearMonth(2026, 8)
    private val date = LocalDate(2026, 8, 5)

    private val repository = RecurringOccurrenceRepository(
        database = db,
        dao = db.recurringOccurrenceDao(),
        mapper = RecurringOccurrenceMapper(),
        transactionRepository = UnusedTransactions,
    )

    private object UnusedTransactions : ITransactionRepository {
        override suspend fun createTransaction(intent: TransactionIntent): Transaction = throw NotImplementedError()
        override fun observeAllTransactions(): Flow<List<Transaction>> = throw NotImplementedError()
        override fun observeTransactionsBy(date: LocalDate?, dimensionId: Long?, accountId: Long?): Flow<List<Transaction>> = throw NotImplementedError()
        override fun observeTransactionById(id: Long): Flow<Transaction?> = throw NotImplementedError()
        override suspend fun getAllTransactions(): List<Transaction> = throw NotImplementedError()
        override suspend fun getTransactionById(id: Long): Transaction? = throw NotImplementedError()
        override suspend fun createTransactions(intents: List<TransactionIntent>): List<Transaction> = throw NotImplementedError()
        override suspend fun updateTransaction(id: Long, title: String?, date: LocalDate, legs: List<TransactionLeg>, contra: ContraLeg?) = throw NotImplementedError()
        override suspend fun deleteTransactionById(id: Long) = throw NotImplementedError()
        override suspend fun deleteTransactionsByIds(ids: List<Long>) = throw NotImplementedError()
    }

    private suspend fun account(type: AccountEntity.Type, currency: String): Long =
        db.accountDao().insert(
            AccountEntity(name = "$type $currency", type = type, currency = currency),
        )

    private suspend fun template(id: Long, amount: Double): Long =
        db.recurringDao().insert(
            RecurringEntity(
                id = id,
                type = RecurringEntity.Type.EXPENSE,
                amount = amount,
                title = "Template $id",
                dayOfMonth = 5,
                categoryId = null,
                accountId = null,
                creditCardId = null,
                createdAt = 0L,
            )
        )

    /**
     * One balanced transaction, the way the write boundary would leave it: the monetary
     * leg on an asset or liability account, the nominal one on income or expense.
     */
    private suspend fun postedCycle(
        recurringId: Long,
        cents: Long,
        currency: String = "BRL",
        nominal: AccountEntity.Type = AccountEntity.Type.EXPENSE,
        status: RecurringOccurrenceEntity.Status = RecurringOccurrenceEntity.Status.CONFIRMED,
        yearMonth: YearMonth = month,
    ): Long {
        val asset = account(AccountEntity.Type.ASSET, currency)
        val nominalAccount = account(nominal, currency)

        val transactionId = db.transactionDao().insert(
            TransactionEntity(title = "Cycle", date = LocalDate(yearMonth.year, yearMonth.month, 5)),
        )

        // Debit-positive: an expense debits its nominal account and credits the asset;
        // an income credits its nominal account and debits the asset.
        val nominalAmount = if (nominal == AccountEntity.Type.EXPENSE) cents else -cents
        db.entryDao().insert(
            EntryEntity(
                transactionId = transactionId,
                accountId = nominalAccount,
                amount = nominalAmount,
                currency = currency,
            )
        )
        db.entryDao().insert(
            EntryEntity(
                transactionId = transactionId,
                accountId = asset,
                amount = -nominalAmount,
                currency = currency,
            )
        )

        db.recurringOccurrenceDao().insert(
            RecurringOccurrenceEntity(
                recurringId = recurringId,
                cycleNumber = 1,
                yearMonth = yearMonth,
                status = status,
                transactionId = transactionId,
                effectiveDate = date,
                handledAt = 0L,
            )
        )

        return transactionId
    }

    @Test
    fun `an overridden cycle sums what the transaction posted, not what the template says`() = runTest {
        template(id = 1L, amount = 940.0)
        postedCycle(recurringId = 1L, cents = 86_500, nominal = AccountEntity.Type.INCOME)

        val settled = repository.settledIn(month)

        assertEquals(MoneyByCurrency.of("BRL", 865.0), settled.income)
        // A zero **in reais**, not the empty figure: the month has movement in reais and
        // none of it was expense, which is a different fact from a month with no movement
        // at all. It is the same shape every other grouped month flow of the app answers
        // in, and the reducer drops a zero term before it reaches a surface.
        assertEquals(MoneyByCurrency.of("BRL", 0.0), settled.expense)
    }

    @Test
    fun `deleting the transaction takes the occurrence with it and the money leaves the month`() = runTest {
        template(id = 1L, amount = 100.0)
        val transactionId = postedCycle(recurringId = 1L, cents = 10_000)

        assertEquals(MoneyByCurrency.of("BRL", 100.0), repository.settledIn(month).expense)

        db.transactionDao().deleteById(transactionId)

        // No hook and no reconciliation: `transactionId` is a real foreign key with
        // `ON DELETE CASCADE`, so the occurrence goes with the transaction, the template
        // is unhandled again and the money is simply not there to sum.
        assertEquals(emptyList(), db.recurringOccurrenceDao().getAll())
        assertEquals(MoneyByCurrency.zero, repository.settledIn(month).expense)
    }

    @Test
    fun `a skipped cycle enters neither nature`() = runTest {
        template(id = 1L, amount = 77.0)
        postedCycle(
            recurringId = 1L,
            cents = 7_700,
            status = RecurringOccurrenceEntity.Status.SKIPPED,
        )

        val settled = repository.settledIn(month)

        assertEquals(MoneyByCurrency.zero, settled.expense)
        assertEquals(MoneyByCurrency.zero, settled.income)
    }

    @Test
    fun `two currencies answer one term each, never one number`() = runTest {
        template(id = 1L, amount = 100.0)
        template(id = 2L, amount = 50.0)
        postedCycle(recurringId = 1L, cents = 10_000, currency = "BRL")
        postedCycle(recurringId = 2L, cents = 5_000, currency = "USD")

        val settled = repository.settledIn(month)

        assertEquals(
            MoneyByCurrency.of(mapOf("BRL" to 100.0, "USD" to 50.0)),
            settled.expense,
        )
    }

    @Test
    fun `the cut is the occurrence's month, and another month's cycle stays out`() = runTest {
        template(id = 1L, amount = 100.0)
        postedCycle(recurringId = 1L, cents = 10_000, yearMonth = YearMonth(2026, 7))

        assertEquals(MoneyByCurrency.zero, repository.settledIn(month).expense)
        assertEquals(
            MoneyByCurrency.of("BRL", 100.0),
            repository.settledIn(YearMonth(2026, 7)).expense,
        )
    }
}
