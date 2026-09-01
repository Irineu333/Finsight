@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.CategoryOverview
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.model.ExchangeRate
import com.neoutils.finsight.domain.model.MoneyByCurrency
import com.neoutils.finsight.domain.model.SpendingVariation
import com.neoutils.finsight.domain.repository.AssetMonthFlowsByCurrency
import com.neoutils.finsight.domain.repository.DimensionFlowsByCurrency
import com.neoutils.finsight.domain.repository.IBaseCurrencyRepository
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.repository.IExchangeRateRepository
import com.neoutils.finsight.domain.repository.LiabilityMonthFlowsByCurrency
import com.neoutils.finsight.domain.repository.ScopeStatsByCurrency
import com.neoutils.finsight.extension.currentYearMonth
import com.neoutils.finsight.extension.today
import com.neoutils.finsight.ui.icons.CategoryLazyIcon
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.minusMonth
import kotlinx.datetime.plusMonth
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * The window, the average and the variation — the rules this use case owns.
 *
 * The series the fake answers is keyed relative to the clock's own month rather than to
 * literal dates: the window is defined against "now", and a suite that hard-coded months
 * would pass or fail depending on when it ran.
 */
class CalculateCategoryOverviewUseCaseTest {

    // Midday mid-month: every zone from UTC−12 to UTC+14 reads the same month from it,
    // so the window's shape does not depend on where the suite runs.
    private val clock = object : Clock {
        override fun now(): Instant = Instant.parse("2026-06-15T12:00:00Z")
    }
    private val now = clock.currentYearMonth()

    private val dimensionId = 10L

    private fun category(isArchived: Boolean = false, type: Category.Type = Category.Type.EXPENSE) =
        Category(
            id = 1L,
            name = "Food",
            icon = CategoryLazyIcon("shopping"),
            type = type,
            createdAt = 0L,
            isArchived = isArchived,
            dimensionId = dimensionId,
        )

    /**
     * A series in the ledger's own convention: debit-positive, so an expense category's
     * months arrive positive and the display sign leaves them as they are.
     */
    private fun useCase(
        series: Map<YearMonth, MoneyByCurrency>,
        rates: Map<String, Double> = emptyMap(),
    ) = CalculateCategoryOverviewUseCase(
        entryRepository = DimensionSeries(dimensionId, series),
        consolidateMoney = reducer(rates = rates),
        clock = clock,
    )

    private fun brl(vararg months: Pair<YearMonth, Double>) =
        months.associate { (month, value) -> month to MoneyByCurrency.of("BRL", value) }

    private fun sole(figure: com.neoutils.finsight.extension.ConsolidatedAmount) =
        figure.terms.single().value

    // --- The window -----------------------------------------------------------------

    @Test
    fun `twelve closed months make a full window`() = runTest {
        // Thirteen closed months of movement: the window takes the last twelve and says
        // twelve, and the thirteenth is outside it.
        val series = brl(*(1..13).map { now.minus(it) to 100.0 }.toTypedArray())

        val overview = assertIs<CategoryOverview.Active>(useCase(series)(category()))
        val window = checkNotNull(overview.window)

        assertEquals(12, window.months)
        assertEquals(1_200.0, sole(window.total))
        assertEquals(100.0, sole(window.average))
    }

    @Test
    fun `a young category has its window shortened to its own age`() = runTest {
        // First entry five closed months ago: dividing by twelve would read as spending
        // less than half of what is spent.
        val series = brl(*(1..5).map { now.minus(it) to 60.0 }.toTypedArray())

        val window = checkNotNull(assertIs<CategoryOverview.Active>(useCase(series)(category())).window)

        assertEquals(5, window.months)
        assertEquals(300.0, sole(window.total))
        assertEquals(60.0, sole(window.average))
    }

    @Test
    fun `a month with no entry counts as zero in the divisor`() = runTest {
        // Six closed months of age, movement in two of them: the average is over six, not
        // over the two that have rows.
        val series = brl(now.minus(6) to 120.0, now.minus(2) to 60.0)

        val window = checkNotNull(assertIs<CategoryOverview.Active>(useCase(series)(category())).window)

        assertEquals(6, window.months)
        assertEquals(180.0, sole(window.total))
        assertEquals(30.0, sole(window.average))
    }

    @Test
    fun `the average times the declared months reproduces the total`() = runTest {
        val series = brl(now.minus(3) to 50.0, now.minus(2) to 30.0, now.minus(1) to 20.0)

        val window = checkNotNull(assertIs<CategoryOverview.Active>(useCase(series)(category())).window)

        assertEquals(3, window.months)
        assertTrue(
            abs(sole(window.average) * window.months - sole(window.total)) < 1e-9,
            "the two figures come from one window, so the user can check one against the other",
        )
    }

    @Test
    fun `the current month stays out of the window`() = runTest {
        val series = brl(now.minus(1) to 100.0, now to 900.0)

        val overview = assertIs<CategoryOverview.Active>(useCase(series)(category()))
        val window = checkNotNull(overview.window)

        assertEquals(1, window.months)
        assertEquals(100.0, sole(window.total), "the half-finished month is not in the window")
        assertEquals(900.0, sole(overview.currentMonth.amount))
    }

    @Test
    fun `a month in two currencies is not summed into one by the ledger`() = runTest {
        val series = mapOf(
            now.minus(1) to MoneyByCurrency.of(mapOf("BRL" to 100.0, "USD" to 20.0)),
        )

        val window = checkNotNull(assertIs<CategoryOverview.Active>(useCase(series)(category())).window)

        assertEquals(
            listOf(100.0, 20.0),
            window.total.terms.map { it.value }.sortedDescending(),
            "no rate reaches the dollars, so the figure keeps both terms instead of inventing one",
        )
    }

    @Test
    fun `the partial month announces the day it was read on`() = runTest {
        val overview = assertIs<CategoryOverview.Active>(
            useCase(brl(now.minus(1) to 10.0, now to 5.0))(category()),
        )

        assertEquals(clock.today().day, overview.currentMonth.elapsedDay)
        assertEquals(now.numberOfDays, overview.currentMonth.daysInMonth)
    }

    // --- The variation --------------------------------------------------------------

    @Test
    fun `the current month is compared against the average, not against last month`() = runTest {
        // Last month was atypical (300); the average of the three closed months is 200,
        // and 240 is 20% above it — not 20% below last month.
        val series = brl(now.minus(3) to 150.0, now.minus(2) to 150.0, now.minus(1) to 300.0, now to 240.0)

        val overview = assertIs<CategoryOverview.Active>(useCase(series)(category()))
        val variation = assertIs<SpendingVariation.Measured>(overview.variation)

        assertEquals(200.0, sole(checkNotNull(overview.window).average))
        assertTrue(abs(variation.fraction - 0.2) < 1e-9)
        assertTrue(variation.isAbove)
    }

    @Test
    fun `a month landing exactly on the average claims no direction`() = runTest {
        // Ordinary, not a curiosity: a purchase in fixed instalments spends the same
        // amount every month, so the month and the average coincide exactly.
        val series = brl(now.minus(2) to 30.0, now.minus(1) to 30.0, now to 30.0)

        val overview = assertIs<CategoryOverview.Active>(useCase(series)(category()))
        val variation = assertIs<SpendingVariation.Measured>(overview.variation)

        assertEquals(0.0, variation.fraction)
        assertTrue(variation.isAtAverage)
        assertFalse(variation.isAbove, "neither above nor below is the honest reading")
    }

    @Test
    fun `a zero average leaves the variation absent rather than zero`() = runTest {
        // Three closed months on file, all netting to zero — an expense and its refund.
        val series = brl(now.minus(3) to 0.0, now.minus(1) to 0.0, now to 40.0)

        val overview = assertIs<CategoryOverview.Active>(useCase(series)(category()))

        assertEquals(SpendingVariation.Absent.ZERO_AVERAGE, overview.variation)
        assertEquals(3, checkNotNull(overview.window).months)
    }

    @Test
    fun `a category with no closed month has no window and no base to compare against`() = runTest {
        val overview = assertIs<CategoryOverview.Active>(useCase(brl(now to 80.0))(category()))

        assertEquals(null, overview.window)
        assertEquals(SpendingVariation.Absent.NO_CLOSED_MONTH, overview.variation)
        assertEquals(80.0, sole(overview.currentMonth.amount))
    }

    @Test
    fun `figures no rate can place on one scale leave the variation absent`() = runTest {
        val series = mapOf(
            now.minus(1) to MoneyByCurrency.of("JPY", 5_000.0),
            now to MoneyByCurrency.of("USD", 40.0),
        )

        val overview = assertIs<CategoryOverview.Active>(useCase(series)(category()))

        assertEquals(SpendingVariation.Absent.NO_COMMON_SCALE, overview.variation)
    }

    @Test
    fun `no absence of variation is ever spelled as zero`() = runTest {
        val absences = listOf(
            useCase(brl(now.minus(3) to 0.0, now to 40.0))(category()),
            useCase(brl(now to 80.0))(category()),
            useCase(
                mapOf(
                    now.minus(1) to MoneyByCurrency.of("JPY", 5_000.0),
                    now to MoneyByCurrency.of("USD", 40.0),
                ),
            )(category()),
        ).map { assertIs<CategoryOverview.Active>(it).variation }

        assertEquals(
            listOf(
                SpendingVariation.Absent.ZERO_AVERAGE,
                SpendingVariation.Absent.NO_CLOSED_MONTH,
                SpendingVariation.Absent.NO_COMMON_SCALE,
            ),
            absences,
        )
        assertTrue(absences.none { it is SpendingVariation.Measured })
    }

    // --- The states -------------------------------------------------------------------

    @Test
    fun `a category never posted to has no figure at all`() = runTest {
        assertEquals(CategoryOverview.Empty, useCase(emptyMap())(category()))
    }

    @Test
    fun `an archived category with no movement is empty too, not a zero`() = runTest {
        assertEquals(CategoryOverview.Empty, useCase(emptyMap())(category(isArchived = true)))
    }

    @Test
    fun `an archived category highlights the whole history over the range it covers`() = runTest {
        val series = brl(now.minus(20) to 100.0, now.minus(9) to 50.0, now.minus(4) to 25.0)

        val overview = assertIs<CategoryOverview.Archived>(useCase(series)(category(isArchived = true)))

        assertEquals(175.0, sole(overview.total), "the whole history, not the window")
        assertEquals(now.minus(20), overview.firstMonth)
        assertEquals(now.minus(4), overview.lastMonth)
    }

    @Test
    fun `an active category highlights the current month`() = runTest {
        val overview = assertIs<CategoryOverview.Active>(
            useCase(brl(now.minus(2) to 100.0, now to 42.5))(category()),
        )

        assertEquals(42.5, sole(overview.currentMonth.amount))
    }

    @Test
    fun `an income category reads its months positive`() = runTest {
        // Income posts in credit, so the ledger answers negative; the display sign is the
        // one the category already reads by.
        val series = brl(now.minus(1) to -300.0, now to -100.0)

        val overview = assertIs<CategoryOverview.Active>(
            useCase(series)(category(type = Category.Type.INCOME)),
        )

        assertEquals(100.0, sole(overview.currentMonth.amount))
        assertEquals(300.0, sole(checkNotNull(overview.window).total))
    }

    // --- The future -------------------------------------------------------------------

    @Test
    fun `instalments dated ahead reach no figure and no count`() = runTest {
        // A purchase in twelve, made two months ago: ten of its instalments are still
        // ahead, and the ledger holds them all.
        val series = brl(*(-2..9).map { offset -> now.shift(offset) to 30.0 }.toTypedArray())

        val overview = assertIs<CategoryOverview.Active>(useCase(series)(category()))
        val window = checkNotNull(overview.window)

        assertEquals(30.0, sole(overview.currentMonth.amount), "only this month's instalment")
        assertEquals(2, window.months, "the window is two closed months old, not twelve")
        assertEquals(60.0, sole(window.total))
    }

    @Test
    fun `the range of an archived category never ends in a month that has not arrived`() = runTest {
        val series = brl(now.minus(2) to 30.0, now.minus(1) to 30.0, now to 30.0, now.plus(1) to 30.0)

        val overview = assertIs<CategoryOverview.Archived>(useCase(series)(category(isArchived = true)))

        assertEquals(now, overview.lastMonth)
        assertEquals(90.0, sole(overview.total), "the pending instalment is not history")
    }
}

private fun YearMonth.minus(months: Int): YearMonth {
    var month = this
    repeat(months) { month = month.minusMonth() }
    return month
}

private fun YearMonth.plus(months: Int): YearMonth {
    var month = this
    repeat(months) { month = month.plusMonth() }
    return month
}

/** Signed month arithmetic: positive is forward, negative backwards. */
private fun YearMonth.shift(months: Int): YearMonth =
    if (months >= 0) plus(months) else minus(-months)

/**
 * A ledger holding one dimension's series. The upper cut is honoured **here**, because
 * that is where it is honoured in production: a fake that answered the future would let
 * this suite pass over a use case that had stopped asking for the cut.
 */
private class DimensionSeries(
    private val dimensionId: Long,
    private val series: Map<YearMonth, MoneyByCurrency>,
) : IEntryRepository {

    override suspend fun dimensionMonthlySeriesByCurrency(
        dimensionId: Long,
        upTo: YearMonth,
    ): Map<YearMonth, MoneyByCurrency> =
        if (dimensionId != this.dimensionId) emptyMap()
        else series.filterKeys { it <= upTo }.toList().sortedBy { it.first }.toMap()

    // Nothing else is this use case's business; reaching any of it is the test telling
    // us it grew a dependency it did not declare.
    override suspend fun dimensionBalanceInMonthByCurrency(month: YearMonth, dimensionId: Long) = throw NotImplementedError()
    override suspend fun getEntriesByTransaction(transactionId: Long): List<Entry> = throw NotImplementedError()
    override fun observeEntriesByTransaction(transactionId: Long): Flow<List<Entry>> = throw NotImplementedError()
    override fun observeLedgerChanges(): Flow<Unit> = throw NotImplementedError()
    override suspend fun hasEntries(accountId: Long) = throw NotImplementedError()
    override suspend fun hasEntriesForDimension(dimensionId: Long) = throw NotImplementedError()
    override suspend fun balance(accountId: Long) = throw NotImplementedError()
    override suspend fun accountFlows(month: YearMonth, accountId: Long, yieldDimensionId: Long?) = throw NotImplementedError()
    override suspend fun accountBalanceUpTo(accountId: Long, target: LocalDate): Double = throw NotImplementedError()
    override suspend fun balanceUpToByCurrency(target: YearMonth, excludedAccountIds: Set<Long>): MoneyByCurrency = throw NotImplementedError()
    override suspend fun naturalBalanceUpToByCurrency(target: YearMonth, type: AccountType, excludedAccountIds: Set<Long>): MoneyByCurrency = throw NotImplementedError()
    override suspend fun dimensionOwedByCurrency(dimensionId: Long): MoneyByCurrency = throw NotImplementedError()
    override suspend fun dimensionFlowsByCurrency(dimensionId: Long): DimensionFlowsByCurrency = throw NotImplementedError()
    override suspend fun owedByDimensionByCurrency(dimensionIds: Collection<Long>): Map<Long, MoneyByCurrency> = throw NotImplementedError()
    override suspend fun flowsByDimensionByCurrency(dimensionIds: Collection<Long>): Map<Long, DimensionFlowsByCurrency> = throw NotImplementedError()
    override suspend fun liabilityMonthFlowsByCurrency(month: YearMonth): LiabilityMonthFlowsByCurrency = throw NotImplementedError()
    override suspend fun assetMonthFlowsByCurrency(month: YearMonth, yieldDimensionId: Long?): AssetMonthFlowsByCurrency = throw NotImplementedError()
    override suspend fun totalsByDimensionByCurrency(
        nominalType: AccountType,
        startDate: LocalDate,
        endDate: LocalDate,
        siblingAccountIds: List<Long>,
    ): Map<Long?, MoneyByCurrency> = throw NotImplementedError()
    override suspend fun totalsByDimensionInMonthByCurrency(
        month: YearMonth,
        nominalType: AccountType,
    ): Map<Long?, MoneyByCurrency> = throw NotImplementedError()
    override suspend fun totalsByDimensionInScopeByCurrency(
        nominalType: AccountType,
        scopeDimensionIds: List<Long>,
    ): Map<Long?, MoneyByCurrency> = throw NotImplementedError()
    override suspend fun scopeStatsByCurrency(
        scopeAccountIds: List<Long>,
        startDate: LocalDate,
        endDate: LocalDate,
    ): ScopeStatsByCurrency = throw NotImplementedError()
}

/** The reducer over an archive holding [rates]. */
private fun reducer(
    base: String = "BRL",
    rates: Map<String, Double> = emptyMap(),
) = ConsolidateMoneyUseCase(
    baseCurrencyRepository = object : IBaseCurrencyRepository {
        private val flow = MutableStateFlow(base)
        override fun observe(): StateFlow<String> = flow
        override suspend fun set(code: String) { flow.value = code }
    },
    exchangeRateRepository = object : IExchangeRateRepository {
        override suspend fun rateAsOf(currency: String, date: LocalDate) = ratesAsOf(date)[currency]
        override suspend fun ratesAsOf(date: LocalDate) = rates.mapValues { (code, rate) ->
            ExchangeRate(
                currency = code,
                counterCurrency = base,
                date = date,
                rate = rate,
                source = ExchangeRate.Source.USER,
            )
        }

        override suspend fun rateBetween(from: String, to: String, date: LocalDate) =
            ratesAsOf(date)[from]?.takeIf { it.counterCurrency == to }
        override fun observeAll(): Flow<List<ExchangeRate>> = flowOf(emptyList())
        override suspend fun save(rate: ExchangeRate) = Unit
        override suspend fun remove(rate: ExchangeRate) = Unit
        override suspend fun countNaming(currency: String) = 0
    },
    getAccountCurrencies = object : GetAccountCurrenciesUseCase {
        override suspend fun invoke() = AccountCurrencies(inUse = listOf(base), ofDefaultAccount = base)
    },
)
