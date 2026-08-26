package com.neoutils.finsight.ui.screen.recurring

import com.neoutils.finsight.consolidator
import com.neoutils.finsight.domain.model.MoneyByCurrency
import com.neoutils.finsight.domain.usecase.RecurringMonthOverview
import com.neoutils.finsight.extension.DisplayAmount
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * **The four figures leave here consolidated, and every one of them can be read.**
 *
 * What the factory owes is what `money-display` and the consolidation layer owe together:
 * a figure the reducer could not reduce is shown in terms and says so, and a month with
 * nothing in it shows a zero the reducer denominated rather than no figure at all.
 */
class RecurringSummaryFactoryTest {

    private val month = YearMonth(2026, 8)

    private fun overview(
        settledExpense: MoneyByCurrency = MoneyByCurrency.zero,
        settledIncome: MoneyByCurrency = MoneyByCurrency.zero,
        forecastExpense: MoneyByCurrency = MoneyByCurrency.zero,
        forecastIncome: MoneyByCurrency = MoneyByCurrency.zero,
    ) = RecurringMonthOverview(
        settledExpense = settledExpense,
        settledIncome = settledIncome,
        forecastExpense = forecastExpense,
        forecastIncome = forecastIncome,
        handled = 0,
        total = 0,
        skipped = 0,
        undenominated = 0,
    )

    @Test
    fun `a month across two currencies with no rate reads in terms, marked`() = runTest {
        val summary = overview(
            settledIncome = MoneyByCurrency.of(mapOf("BRL" to 5_865.0, "USD" to 50.0)),
        ).toSummary(
            month = month,
            consolidate = consolidator(inUse = arrayOf("BRL", "USD")),
        )

        assertEquals(2, summary.settledIncome.terms.size)
        assertTrue(summary.settledIncome.isApproximate)
        // And the card has something to explain: the badge decides its own level off the
        // figures it is handed, and this is the level that earns it.
        assertTrue(summary.figures.any { it.isApproximate })
    }

    @Test
    fun `a month with no movement reads zero, denominated, and does not disappear`() = runTest {
        val summary = overview().toSummary(month = month, consolidate = consolidator())

        // One term, not none: the figure is the answer "nothing happened", and a card
        // that dropped it would be a card with a hole in it.
        assertEquals(1, summary.settledExpense.terms.size)
        assertEquals(0.0, summary.settledExpense.terms.single().value)
        assertEquals("BRL", summary.settledExpense.terms.single().currency)
        assertFalse(summary.settledExpense.isApproximate)
    }

    @Test
    fun `every figure is a magnitude - the card shows no total for a sign to answer to`() = runTest {
        val summary = overview(
            settledExpense = MoneyByCurrency.of("BRL", 1_240.0),
            settledIncome = MoneyByCurrency.of("BRL", 865.0),
            forecastExpense = MoneyByCurrency.of("BRL", 380.0),
            forecastIncome = MoneyByCurrency.of("BRL", 50.0),
        ).toSummary(month = month, consolidate = consolidator())

        assertTrue(
            summary.figures.all { figure ->
                figure.terms.all { it.policy == DisplayAmount.SignPolicy.MAGNITUDE }
            },
        )
    }
}
