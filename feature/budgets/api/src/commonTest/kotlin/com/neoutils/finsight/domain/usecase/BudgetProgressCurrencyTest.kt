package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.Budget
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.CurrencyBalance
import com.neoutils.finsight.domain.model.ExchangeRate
import com.neoutils.finsight.domain.repository.IExchangeRateRepository
import com.neoutils.finsight.ui.icons.CategoryLazyIcon
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The profile the rule exists for, and its opposite.
 *
 * A budget's limit is denominated by the accounts the user actually spends from, never by the
 * base (design D13). The consequence is the point of this suite: a user with **everything in
 * dollars** on a device whose locale resolves reais states the limit in dollars, spends in
 * dollars, and reads a progress bar that is **exact and unmarked** — they are single-currency,
 * and they pay none of the price of multi-currency, not even through the entry.
 *
 * The opposite profile pays it, and visibly: spending across two currencies is reduced to the
 * limit's, so the bar is one number and the number is an approximation, marked as one.
 */
class BudgetProgressCurrencyTest {

    private val month = YearMonth(2026, 3)
    private val today = LocalDate(2026, 4, 2)

    private fun category(id: Long, dimensionId: Long) = Category(
        id = id, name = "Cat$id", icon = CategoryLazyIcon("shopping"),
        type = Category.Type.EXPENSE, createdAt = 0L, dimensionId = dimensionId,
    )

    private val food = category(1, dimensionId = 10)

    private fun budgetIn(currency: String) = Budget(
        id = 1, title = "Food", categories = listOf(food), iconKey = "shopping",
        amount = 200.0, currency = currency, createdAt = 0L,
    )

    private suspend fun progressOf(
        budget: Budget,
        spending: CurrencyBalance,
        rates: Map<String, Double> = emptyMap(),
    ) = CalculateBudgetProgressUseCase(
        entryRepository = DimensionSpending(month, mapOf(10L to spending)),
        consolidateFigure = ConsolidateFigureUseCase(FlatRates(rates)),
    )(budgets = listOf(budget), month = month, today = today).single()

    @Test
    fun `everything in one currency that is not the base reads exact and unmarked`() = runTest {
        // A dollar rate is on file, and it takes no part: there was never more than one
        // currency to reconcile, so converting would trade an exact number for an approximate
        // one in exchange for nothing.
        val progress = progressOf(
            budget = budgetIn("USD"),
            spending = CurrencyBalance.of("USD", 50.0),
            rates = mapOf("USD" to 5.5),
        )

        assertEquals(50.0, progress.spent.comparable)
        assertEquals(0.25f, progress.progress)
        assertEquals(150.0, progress.remaining)
        assertFalse(progress.isExceeded)
        assertFalse(progress.isApproximate, "nothing was reconciled, so nothing is approximate")
    }

    @Test
    fun `spending across two currencies is reduced to the limit's, and says it was`() = runTest {
        // 50 USD at 5.50 plus 25 BRL, against a limit stated in reais.
        val progress = progressOf(
            budget = budgetIn("BRL"),
            spending = CurrencyBalance.of(mapOf("BRL" to 25.0, "USD" to 50.0)),
            rates = mapOf("USD" to 5.5),
        )

        assertEquals(300.0, progress.spent.comparable)
        assertTrue(progress.isExceeded)
        assertTrue(progress.isApproximate, "a rate took part, so the bar is an approximation")
    }

    @Test
    fun `a share the rates could not reach is left out of the bar and declared`() = runTest {
        // No dollar rate: the reais share is all the bar can honestly divide by, and
        // `isPartial` is what carries that into the fraction instead of leaving it implicit.
        val progress = progressOf(
            budget = budgetIn("BRL"),
            spending = CurrencyBalance.of(mapOf("BRL" to 100.0, "USD" to 50.0)),
        )

        assertEquals(100.0, progress.spent.comparable)
        assertTrue(progress.spent.isPartial)
        assertTrue(progress.isApproximate)
        // And nothing was invented: the dollars are still in the figure, in dollars.
        assertEquals(2, progress.spent.figure.terms.size)
    }
}

/** One dimension's per-currency spending in the month asked about, and nothing else. */
private class DimensionSpending(
    private val month: YearMonth,
    private val spending: Map<Long, CurrencyBalance>,
) : StubEntryRepository() {
    override suspend fun dimensionBalanceInMonth(month: YearMonth, dimensionId: Long): CurrencyBalance =
        if (month == this.month) spending[dimensionId] ?: CurrencyBalance.zero else CurrencyBalance.zero
}

/** One rate per currency, in force forever. */
private class FlatRates(private val rates: Map<String, Double>) : IExchangeRateRepository {
    override suspend fun rateOn(currency: String, date: LocalDate) =
        rates[currency]?.let { ExchangeRate(currency, date, it, ExchangeRate.Source.USER) }

    override fun observeAll() = throw NotImplementedError()
    override suspend fun getAll() = throw NotImplementedError()
    override suspend fun record(rate: ExchangeRate) = throw NotImplementedError()
    override suspend fun remove(rate: ExchangeRate) = throw NotImplementedError()
}
