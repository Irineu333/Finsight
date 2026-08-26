@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.FakeRecurringOccurrenceRepository
import com.neoutils.finsight.domain.model.MoneyByCurrency
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.model.RecurringOccurrence
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.domain.repository.RecurringSettledMoney
import com.neoutils.finsight.recurring
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.ExperimentalTime

/**
 * **The two halves of a month, and what neither of them can hold.**
 *
 * The fact is money and the forecast is templates, and the asymmetry between them is the
 * whole subject: a template archived after confirming keeps its posting in the month and
 * stops generating a claim on it, which is exactly what archiving promises the user.
 *
 * The skipped cycle is the third state, invisible in all four figures by construction —
 * so the counter is the only place it is representable, and these tests are where that is
 * pinned down.
 */
class GetRecurringMonthOverviewUseCaseTest {

    private val month = YearMonth(2026, 8)

    private val occurrences = FakeRecurringOccurrenceRepository()

    private val useCase = GetRecurringMonthOverviewUseCase(
        getUnhandledRecurring = GetUnhandledRecurringUseCase(),
        occurrenceRepository = occurrences,
    )

    /** Everything is denominated in reais unless the test says a template names nothing. */
    private val inReais: (Recurring) -> String? = { "BRL" }

    private fun occurrence(
        recurringId: Long,
        status: RecurringOccurrence.Status = RecurringOccurrence.Status.CONFIRMED,
        yearMonth: YearMonth = month,
    ) = RecurringOccurrence(
        recurringId = recurringId,
        cycleNumber = 1,
        yearMonth = yearMonth,
        status = status,
        effectiveDate = LocalDate(yearMonth.year, yearMonth.month, 5),
        handledAt = 0L,
    )

    @Test
    fun `an untreated template whose day has not come is still the month's forecast`() = runTest {
        // Day 28 of a month whose 10th it is: the app would not call it *pending*, and
        // the card is not asking that question. It is asking what the month will still
        // ask for, and a commitment on the 28th is money that leaves all the same.
        val late = recurring(id = 1L, amount = 380.0).copy(dayOfMonth = 28)

        val overview = useCase(listOf(late), emptyList(), month, inReais)

        assertEquals(MoneyByCurrency.of("BRL", 380.0), overview.forecastExpense)
        assertEquals(1, overview.total)
        assertEquals(0, overview.handled)
    }

    @Test
    fun `a template already handled this month composes no forecast figure`() = runTest {
        val confirmed = recurring(id = 1L, amount = 100.0)
        val skipped = recurring(id = 2L, amount = 77.0)
        occurrences.all.value = listOf(
            occurrence(confirmed.id),
            occurrence(skipped.id, RecurringOccurrence.Status.SKIPPED),
        )

        val overview = useCase(listOf(confirmed, skipped), occurrences.all.value, month, inReais)

        assertEquals(MoneyByCurrency.zero, overview.forecastExpense)
        assertEquals(MoneyByCurrency.zero, overview.forecastIncome)
    }

    @Test
    fun `an archived template leaves the forecast and its posting stays in the fact`() = runTest {
        val archived = recurring(id = 1L, amount = 940.0, isArchived = true)
        // The cycle was confirmed before the archiving, in this very month.
        occurrences.all.value = listOf(occurrence(archived.id))
        occurrences.settled = RecurringSettledMoney(
            expense = MoneyByCurrency.of("BRL", 940.0),
            income = MoneyByCurrency.zero,
        )

        val overview = useCase(listOf(archived), occurrences.all.value, month, inReais)

        assertEquals(MoneyByCurrency.of("BRL", 940.0), overview.settledExpense)
        assertEquals(MoneyByCurrency.zero, overview.forecastExpense)
        // And it is not counted either: the counter is about the templates the month can
        // still ask something of.
        assertEquals(0, overview.total)
    }

    @Test
    fun `a template no account denominates leaves the sum and is declared`() = runTest {
        val denominated = recurring(id = 1L, amount = 100.0)
        val orphan = recurring(id = 2L, amount = 500.0)

        val overview = useCase(
            recurringList = listOf(denominated, orphan),
            occurrences = emptyList(),
            month = month,
            currencyOf = { if (it.id == orphan.id) null else "BRL" },
        )

        assertEquals(MoneyByCurrency.of("BRL", 100.0), overview.forecastExpense)
        assertEquals(1, overview.undenominated)
    }

    @Test
    fun `a skipped cycle counts as handled and is declared apart`() = runTest {
        val confirmed = recurring(id = 1L, amount = 100.0)
        val skipped = recurring(id = 2L, amount = 77.0)
        val untouched = recurring(id = 3L, amount = 50.0, type = TransactionType.INCOME)
        occurrences.all.value = listOf(
            occurrence(confirmed.id),
            occurrence(skipped.id, RecurringOccurrence.Status.SKIPPED),
        )

        val overview = useCase(
            recurringList = listOf(confirmed, skipped, untouched),
            occurrences = occurrences.all.value,
            month = month,
            currencyOf = inReais,
        )

        assertEquals(3, overview.total)
        assertEquals(2, overview.handled)
        assertEquals(1, overview.skipped)
        // The skipped 77 is in no figure at all — which is the arithmetic the counter
        // exists to account for.
        assertEquals(MoneyByCurrency.zero, overview.forecastExpense)
        assertEquals(MoneyByCurrency.of("BRL", 50.0), overview.forecastIncome)
    }

    @Test
    fun `a month before the template existed carries neither figure nor count`() = runTest {
        // The month selector of the card reaches any year; the series does not.
        val bornInAugust = recurring(id = 1L, amount = 380.0).copy(
            createdAt = LocalDate(2026, 8, 1)
                .atStartOfDayIn(TimeZone.currentSystemDefault())
                .toEpochMilliseconds(),
        )

        val overview = useCase(listOf(bornInAugust), emptyList(), YearMonth(2026, 3), inReais)

        assertEquals(MoneyByCurrency.zero, overview.forecastExpense)
        assertEquals(0, overview.total)
        assertEquals(0, overview.handled)
    }

    @Test
    fun `an occurrence of another month neither handles nor is skipped here`() = runTest {
        val template = recurring(id = 1L, amount = 100.0)
        occurrences.all.value = listOf(
            occurrence(template.id, RecurringOccurrence.Status.SKIPPED, YearMonth(2026, 7)),
        )

        val overview = useCase(listOf(template), occurrences.all.value, month, inReais)

        assertEquals(0, overview.handled)
        assertEquals(0, overview.skipped)
        assertEquals(MoneyByCurrency.of("BRL", 100.0), overview.forecastExpense)
    }
}
