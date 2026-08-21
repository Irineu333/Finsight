@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.error.RecurringError
import com.neoutils.finsight.domain.exception.RecurringException
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.model.RecurringOccurrence
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionIntent
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.domain.model.form.RecurringForm
import com.neoutils.finsight.domain.repository.IRecurringRepository
import com.neoutils.finsight.extension.toYearMonth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.yearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * A transaction that opens a recurring has to leave the month it was written in
 * *settled*: the template anchored on its own date, the cycle numbered 1, and the
 * occurrence recorded as confirmed. What this guards is the duplicate — a month left
 * unrecorded comes back as pending and puts the same expense in the ledger twice.
 */
class StartRecurringFromTransactionUseCaseTest {

    private class Recorder : IRecurringRepository {
        val created = mutableListOf<Triple<Recurring, TransactionIntent, RecurringOccurrence>>()

        override suspend fun createWithFirstCycle(
            recurring: Recurring,
            firstCycle: TransactionIntent,
            occurrence: RecurringOccurrence,
        ): Transaction {
            // The identity the repository would produce; the fields under test are the
            // ones it receives.
            created += Triple(recurring.copy(id = 7L), firstCycle, occurrence.copy(recurringId = 7L))
            return Transaction(id = 42L, title = firstCycle.title, date = firstCycle.date)
        }

        override fun observeAllRecurring(): Flow<List<Recurring>> = throw NotImplementedError()
        override fun observeRecurringById(id: Long): Flow<Recurring?> = throw NotImplementedError()
        override suspend fun getRecurringById(id: Long): Recurring? = throw NotImplementedError()
        override suspend fun hasRecurringForAccount(accountId: Long): Boolean = throw NotImplementedError()
        override suspend fun hasRecurringForCreditCard(creditCardId: Long): Boolean = throw NotImplementedError()
        override suspend fun hasRecurringForCategory(categoryId: Long): Boolean = throw NotImplementedError()
        override suspend fun hasTransactionForRecurring(recurringId: Long): Boolean = throw NotImplementedError()
        override suspend fun insert(recurring: Recurring) = throw NotImplementedError()
        override suspend fun update(recurring: Recurring) = throw NotImplementedError()
        override suspend fun delete(recurring: Recurring) = throw NotImplementedError()
    }

    private val account = Account(
        id = 1L,
        name = "Checking",
        type = AccountType.ASSET,
        currency = "BRL",
    )

    private val today = LocalDate(2026, 8, 12)

    private val clock = object : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(HANDLED_AT)
    }

    private fun form(
        amount: String = "240000",
        title: String = "Rent",
        day: Int = 12,
    ) = RecurringForm(
        type = TransactionType.EXPENSE,
        amount = amount,
        title = title,
        dayOfMonth = day.toString(),
        account = account,
        creditCard = null,
        category = null,
    )

    private fun intent(date: LocalDate) = TransactionIntent(
        title = "Rent",
        date = date,
        legs = emptyList(),
        contra = null,
    )

    @Test
    fun `anchors the template on the transaction's own date`() = runTest {
        val recorder = Recorder()

        StartRecurringFromTransactionUseCase(recorder, clock)(form(), intent(today))

        val (recurring, _, _) = recorder.created.single()
        assertEquals(today.yearMonth, Instant.fromEpochMilliseconds(recurring.createdAt).toYearMonth())
        assertEquals(today.day, recurring.dayOfMonth)
        assertEquals(2400.0, recurring.amount)
    }

    @Test
    fun `the transaction is cycle 1, and its month is recorded as confirmed`() = runTest {
        val recorder = Recorder()

        val result = StartRecurringFromTransactionUseCase(recorder, clock)(form(), intent(today))

        val (_, firstCycle, occurrence) = recorder.created.single()
        assertEquals(42L, result.getOrNull()?.id)
        assertEquals(1, firstCycle.recurringCycle)
        assertEquals(1, occurrence.cycleNumber)
        assertEquals(RecurringOccurrence.Status.CONFIRMED, occurrence.status)
        assertEquals(today.yearMonth, occurrence.yearMonth)
        assertEquals(today, occurrence.effectiveDate)
        assertEquals(HANDLED_AT, occurrence.handledAt)
    }

    /**
     * The cycle is not a constant written down on the side: it is
     * `createdAt→month .monthsUntil(month) + 1` in its degenerate case. A backdated
     * transaction therefore opens its own month, not the current one.
     */
    @Test
    fun `a backdated transaction opens the cycle in the month of its date`() = runTest {
        val recorder = Recorder()
        val backdated = LocalDate(2026, 6, 3)

        StartRecurringFromTransactionUseCase(recorder, clock)(form(day = 3), intent(backdated))

        val (recurring, _, occurrence) = recorder.created.single()
        assertEquals(backdated.yearMonth, occurrence.yearMonth)
        assertEquals(1, occurrence.cycleNumber)
        assertEquals(
            backdated.yearMonth,
            Instant.fromEpochMilliseconds(recurring.createdAt).toYearMonth(),
        )
    }

    @Test
    fun `the month the transaction settled is not offered as pending`() = runTest {
        val recorder = Recorder()

        StartRecurringFromTransactionUseCase(recorder, clock)(form(), intent(today))

        val (recurring, _, occurrence) = recorder.created.single()
        val pending = GetPendingRecurringUseCase(GetUnhandledRecurringUseCase())(
            recurringList = listOf(recurring),
            occurrences = listOf(occurrence),
            today = today,
        )

        assertTrue(pending.isEmpty())
    }

    /**
     * The other half of the same rule: a template opened by a backdated transaction has
     * a current cycle nobody has written yet, and it is right for that one to show up.
     */
    @Test
    fun `a backdated transaction leaves the current month pending`() = runTest {
        val recorder = Recorder()
        val backdated = LocalDate(2026, 6, 3)

        StartRecurringFromTransactionUseCase(recorder, clock)(form(day = 3), intent(backdated))

        val (recurring, _, occurrence) = recorder.created.single()
        val pending = GetPendingRecurringUseCase(GetUnhandledRecurringUseCase())(
            recurringList = listOf(recurring),
            occurrences = listOf(occurrence),
            today = today,
        )

        assertEquals(listOf(recurring), pending)
    }

    @Test
    fun `an unnameable template is refused before anything is written`() = runTest {
        val recorder = Recorder()

        val result = StartRecurringFromTransactionUseCase(recorder, clock)(
            form = form(title = ""),
            firstCycle = intent(today),
        )

        val error = assertIs<RecurringException>(result.leftOrNull())
        assertEquals(RecurringError.TITLE_OR_CATEGORY_REQUIRED, error.error)
        assertTrue(recorder.created.isEmpty())
    }

    private companion object {
        const val HANDLED_AT = 1_770_000_000_000L
    }
}
