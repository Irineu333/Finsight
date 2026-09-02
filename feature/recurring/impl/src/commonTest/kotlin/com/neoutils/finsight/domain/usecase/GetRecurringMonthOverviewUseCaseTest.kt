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
 * **The two halves of a month, and the partition both of them are drawn from.**
 *
 * The fact is money and the forecast is templates, and the asymmetry between them is the
 * whole subject: a template archived after confirming keeps its posting in the month and
 * stops generating a claim on it, which is exactly what archiving promises the user.
 *
 * The forecast is not recomputed here. It is the cycles the month has nothing recorded
 * for, straight off [GetRecurringCyclesUseCase] — so whoever lists the rows and whoever
 * projects the money are looking at one set, and a skipped cycle is absent from the
 * figures by the same rule that puts it in its own section.
 */
class GetRecurringMonthOverviewUseCaseTest {

    private val month = YearMonth(2026, 8)
    private val today = LocalDate(2026, 8, 10)

    private val occurrences = FakeRecurringOccurrenceRepository()

    private val cyclesOf = GetRecurringCyclesUseCase(GetUnhandledRecurringUseCase())
    private val useCase = GetRecurringMonthOverviewUseCase(occurrenceRepository = occurrences)

    /** Everything is denominated in reais unless the test says a template names nothing. */
    private val inReais: (Recurring) -> String? = { "BRL" }

    private suspend fun overviewOf(
        recurringList: List<Recurring>,
        month: YearMonth = this.month,
        currencyOf: (Recurring) -> String? = inReais,
    ) = useCase(
        cycles = cyclesOf(
            recurringList = recurringList,
            occurrences = occurrences.all.value,
            month = month,
            today = today,
        ),
        currencyOf = currencyOf,
    )

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

        val overview = overviewOf(listOf(late))

        assertEquals(MoneyByCurrency.of("BRL", 380.0), overview.forecastExpense)
    }

    @Test
    fun `a template already handled this month composes no forecast figure`() = runTest {
        val confirmed = recurring(id = 1L, amount = 100.0)
        val skipped = recurring(id = 2L, amount = 77.0)
        occurrences.all.value = listOf(
            occurrence(confirmed.id),
            occurrence(skipped.id, RecurringOccurrence.Status.SKIPPED),
        )

        val overview = overviewOf(listOf(confirmed, skipped))

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

        val overview = overviewOf(listOf(archived))

        assertEquals(MoneyByCurrency.of("BRL", 940.0), overview.settledExpense)
        assertEquals(MoneyByCurrency.zero, overview.forecastExpense)
    }

    @Test
    fun `a template no account denominates leaves the sum and is declared`() = runTest {
        val denominated = recurring(id = 1L, amount = 100.0)
        val orphan = recurring(id = 2L, amount = 500.0)

        val overview = overviewOf(
            recurringList = listOf(denominated, orphan),
            currencyOf = { if (it.id == orphan.id) null else "BRL" },
        )

        assertEquals(MoneyByCurrency.of("BRL", 100.0), overview.forecastExpense)
        assertEquals(1, overview.undenominated)
    }

    /**
     * The skipped cycle is invisible in all four figures by construction — no entry was
     * written and the month is answered — and that is correct. Where it *is* representable
     * is its own section of the list.
     */
    @Test
    fun `a skipped cycle composes no figure at all`() = runTest {
        val confirmed = recurring(id = 1L, amount = 100.0)
        val skipped = recurring(id = 2L, amount = 77.0)
        val untouched = recurring(id = 3L, amount = 50.0, type = TransactionType.INCOME)
        occurrences.all.value = listOf(
            occurrence(confirmed.id),
            occurrence(skipped.id, RecurringOccurrence.Status.SKIPPED),
        )

        val overview = overviewOf(listOf(confirmed, skipped, untouched))

        assertEquals(MoneyByCurrency.zero, overview.forecastExpense)
        assertEquals(MoneyByCurrency.of("BRL", 50.0), overview.forecastIncome)
    }

    @Test
    fun `a month before the template existed carries no figure`() = runTest {
        // The month selector reaches any year; the series does not.
        val bornInAugust = recurring(id = 1L, amount = 380.0).copy(
            createdAt = LocalDate(2026, 8, 1)
                .atStartOfDayIn(TimeZone.currentSystemDefault())
                .toEpochMilliseconds(),
        )

        val overview = overviewOf(listOf(bornInAugust), month = YearMonth(2026, 3))

        assertEquals(MoneyByCurrency.zero, overview.forecastExpense)
    }

    @Test
    fun `an occurrence of another month does not handle this one`() = runTest {
        val template = recurring(id = 1L, amount = 100.0)
        occurrences.all.value = listOf(
            occurrence(template.id, RecurringOccurrence.Status.SKIPPED, YearMonth(2026, 7)),
        )

        val overview = overviewOf(listOf(template))

        assertEquals(MoneyByCurrency.of("BRL", 100.0), overview.forecastExpense)
    }
}
