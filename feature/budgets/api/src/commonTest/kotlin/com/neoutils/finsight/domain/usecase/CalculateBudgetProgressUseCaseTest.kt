package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.ExchangeRate
import com.neoutils.finsight.domain.repository.IBaseCurrencyRepository
import com.neoutils.finsight.domain.repository.IExchangeRateRepository
import com.neoutils.finsight.domain.usecase.ConsolidateMoneyUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Budget
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.model.LimitType
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionRecurring
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.ui.icons.CategoryLazyIcon
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import com.neoutils.finsight.domain.repository.IEntryRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import com.neoutils.finsight.domain.model.MoneyByCurrency
import com.neoutils.finsight.domain.repository.AssetMonthFlowsByCurrency
import com.neoutils.finsight.domain.repository.DimensionFlowsByCurrency
import com.neoutils.finsight.domain.repository.LiabilityMonthFlowsByCurrency
import com.neoutils.finsight.domain.repository.ScopeStatsByCurrency

/**
 * Characterizes the `spent` figure of [CalculateBudgetProgressUseCase]: Σ entries
 * carrying each budgeted category's dimension, in the month. The use case reads the
 * ledger itself now (task 9.2) — `:core:ledger` is a core, so an `api` may depend on
 * it — instead of being handed the map by three separate callers. The number (42.5)
 * must survive both moves.
 */
class CalculateBudgetProgressUseCaseTest {

    private val month = YearMonth(2026, 3)

    private fun useCase(balances: Map<Long, Double> = emptyMap()) =
        CalculateBudgetProgressUseCase(MonthBalances(month, balances), reducer())

    private fun useCase(
        multi: Map<Long, Map<String, Double>>,
        rates: Map<String, Double> = emptyMap(),
    ) = CalculateBudgetProgressUseCase(
        MonthBalances(month, emptyMap(), multi),
        reducer(rates = rates),
    )

    private fun category(id: Long, dimensionId: Long) = Category(
        id = id, name = "Cat$id", icon = CategoryLazyIcon("shopping"),
        type = Category.Type.EXPENSE, createdAt = 0L, dimensionId = dimensionId,
    )

    private val budget = Budget(
        id = 1, title = "Food & Transport",
        categories = listOf(category(1, dimensionId = 10), category(2, dimensionId = 11)),
        iconKey = "shopping", amount = 200.0, currency = "BRL",
        limitType = LimitType.FIXED, createdAt = 0L,
    )

    @Test
    fun `spent sums the month's entries carrying the budget's category dimensions`() = runTest {
        // Nominal legs are debit-positive: the ledger read is already +spent.
        // Dimension 10 spent 30.0 and 11 spent 12.5 → 42.5. Dimension 12 is outside
        // the budget, and any other month is excluded by the read itself.
        val progress = useCase(mapOf(10L to 30.0, 11L to 12.5, 12L to 99.0))(
            budgets = listOf(budget),
            month = month,
        ).single()

        assertEquals(42.5, progress.spent)
        assertEquals(200.0, progress.budget.amount)
    }

    @Test
    fun `categories with no movement contribute nothing`() = runTest {
        val budgetWithUnposted = budget.copy(
            categories = listOf(category(1, dimensionId = 10), category(3, dimensionId = 12)),
        )

        val progress = useCase(mapOf(10L to 30.0))(
            budgets = listOf(budgetWithUnposted),
            month = month,
        ).single()

        assertEquals(30.0, progress.spent)
    }

    @Test
    fun `a percentage limit reads the confirmation of the month being looked at`() = runTest {
        val salary = Recurring(
            id = 7, type = TransactionType.INCOME, amount = 1000.0, title = "Salary",
            dayOfMonth = 5, category = null, account = null, creditCard = null, createdAt = 0L,
        )
        val percentageBudget = budget.copy(
            limitType = LimitType.PERCENTAGE, percentage = 50.0, recurringId = salary.id,
        )
        // March was confirmed at 2000 (a bonus); February was never confirmed and must
        // fall back to the recurring's own 1000 — reading March's confirmation there
        // would wrongly yield 1000.0 instead of 500.0.
        val marchConfirmation = confirmedTransaction(salary, LocalDate(2026, 3, 5), cents = 200_000)

        val february = useCase()(
            budgets = listOf(percentageBudget),
            recurringList = listOf(salary),
            transactions = listOf(marchConfirmation),
            month = YearMonth(2026, 2),
        ).single()

        assertEquals(500.0, february.budget.amount)

        val march = useCase()(
            budgets = listOf(percentageBudget),
            recurringList = listOf(salary),
            transactions = listOf(marchConfirmation),
            month = month,
        ).single()

        assertEquals(1000.0, march.budget.amount)
    }

    private fun confirmedTransaction(recurring: Recurring, date: LocalDate, cents: Long) = Transaction(
        id = 1, title = recurring.title, date = date,
        recurringId = recurring.id, recurringCycle = 1,
        entries = listOf(
            Entry(account = Account(id = 100, name = "Checking", type = AccountType.ASSET, currency = "BRL"), amount = -cents),
            Entry(account = Account(id = 101, name = "Salary", type = AccountType.INCOME, currency = "BRL"), amount = cents),
        ),
    )

    /**
     * The single-currency user pays nothing for multi-currency. The spending is already
     * in the limit's currency, so nothing is converted, no rate is read, and the figure
     * is **exact** — even with a rate sitting in the archive.
     */
    @Test
    fun `spending already in the limit's currency is exact`() = runTest {
        val progress = useCase(
            multi = mapOf(10L to mapOf("BRL" to 30.0), 11L to mapOf("BRL" to 12.5)),
            rates = mapOf("USD" to 5.0),
        )(budgets = listOf(budget), month = month).single()

        assertEquals(42.5, progress.spent)
        assertEquals(false, progress.isApproximate)
        assertEquals(false, progress.hasUnpricedSpending)
    }

    /**
     * **The limit's currency is the target, not the base.** A budget in dollars whose
     * spending happened in reais is reduced into dollars — triangulated over the base,
     * which is where rates are stored — and marked approximate because a rate took part.
     */
    @Test
    fun `spending in another currency is reduced into the limit's currency`() = runTest {
        val inDollars = budget.copy(currency = "USD", amount = 100.0)

        val progress = useCase(
            multi = mapOf(10L to mapOf("BRL" to 100.0)),
            rates = mapOf("USD" to 5.0),
        )(budgets = listOf(inDollars), month = month).single()

        assertEquals(20.0, progress.spent, "R$ 100 at 5,00 is US$ 20")
        assertEquals(true, progress.isApproximate)
        assertEquals(false, progress.hasUnpricedSpending)
    }

    /**
     * Part of the spending in a currency no rate reaches makes the bar a **floor**, and
     * the flag is what lets the surface say so. Leaving it out silently would read as
     * "less spent than you have", which is the one direction a budget must not err in —
     * and inventing a rate of `1` for it would be worse.
     *
     * **And what remains is not approximate.** This test used to assert that it was, which
     * marked an exact number: the R$ 30 of a BRL budget were always in BRL and no rate
     * touched them. "Some of it could not be priced" and "this number went through a rate"
     * are different facts, and `hasUnpricedSpending` is the one that says the first.
     */
    @Test
    fun `spending no rate reaches is left out and the progress says so`() = runTest {
        val progress = useCase(
            multi = mapOf(10L to mapOf("BRL" to 30.0), 11L to mapOf("JPY" to 5000.0)),
        )(budgets = listOf(budget), month = month).single()

        assertEquals(30.0, progress.spent, "only what could be priced is in the number")
        assertEquals(false, progress.isApproximate, "no rate touched the 30 — it was always BRL")
        assertEquals(true, progress.hasUnpricedSpending)

        val figure = progress.spentFigure!!
        assertEquals(listOf("BRL", "JPY"), figure.terms.map { it.currency })
        assertEquals(
            emptyList(),
            figure.terms.filter { it.isApproximate }.map { it.currency },
            "neither term went through a rate, so neither wears the mark",
        )
        assertEquals(
            true,
            figure.isApproximate,
            "the figure still is: it holds parts that do not add up, and no single number answers for it",
        )
    }

    /**
     * **A zero in an unpriced currency is not unpriced spending.** It went through the same
     * "keep a lone zero" rule as the dashboard's, and the consequence here was louder than
     * a wrong symbol: a category whose only foreign entries net to zero came back with the
     * part-that-could-not-be-priced flag raised, which blanks the whole bar to `***` and
     * turns an exactly-known R$ 30 into "we cannot tell you".
     *
     * There is nothing to price. Zero is zero at any rate, including one that does not
     * exist.
     */
    @Test
    fun `spending that nets to zero in another currency does not make the bar unknown`() = runTest {
        val progress = useCase(
            multi = mapOf(10L to mapOf("BRL" to 30.0), 11L to mapOf("JPY" to 0.0)),
        )(budgets = listOf(budget), month = month).single()

        assertEquals(30.0, progress.spent)
        assertEquals(false, progress.hasUnpricedSpending, "a zero needs no rate to be included")
        assertEquals(false, progress.isApproximate)
        assertEquals(listOf("BRL"), progress.spentFigure!!.terms.map { it.currency })
    }
}

/**
 * The one ledger read this use case makes: the month balance of each dimension it
 * is asked about. Anything not seeded reads zero, which is what "no movement" is.
 */
private class MonthBalances(
    private val month: YearMonth,
    private val balances: Map<Long, Double>,
    private val multi: Map<Long, Map<String, Double>> = emptyMap(),
) : IEntryRepository {
    override suspend fun dimensionBalanceInMonthByCurrency(month: YearMonth, dimensionId: Long) =
        if (month == this.month) {
            multi[dimensionId]
                ?.let { com.neoutils.finsight.domain.model.MoneyByCurrency.of(it) }
                ?: balances[dimensionId]
                    ?.let { com.neoutils.finsight.domain.model.MoneyByCurrency.of("BRL", it) }
                ?: com.neoutils.finsight.domain.model.MoneyByCurrency.zero
        } else {
            com.neoutils.finsight.domain.model.MoneyByCurrency.zero
        }

    // Nothing else is this use case's business; reaching any of it is the test
    // telling us the use case grew a dependency it did not declare.
    override suspend fun getEntriesByTransaction(transactionId: Long) = throw NotImplementedError()
    override fun observeEntriesByTransaction(transactionId: Long) = throw NotImplementedError()
    override fun observeLedgerChanges() = throw NotImplementedError()
    override suspend fun hasEntries(accountId: Long) = throw NotImplementedError()
    override suspend fun hasEntriesForDimension(dimensionId: Long) = throw NotImplementedError()
    override suspend fun balance(accountId: Long) = throw NotImplementedError()
    override suspend fun accountFlows(month: YearMonth, accountId: Long) = throw NotImplementedError()
    override suspend fun dimensionEntryCountInMonth(month: YearMonth, dimensionId: Long) = throw NotImplementedError()

    override suspend fun accountBalanceUpTo(accountId: Long, target: YearMonth): Double = throw NotImplementedError()
    override suspend fun balanceUpToByCurrency(target: YearMonth): MoneyByCurrency = throw NotImplementedError()
    override suspend fun naturalBalanceUpToByCurrency(target: YearMonth, type: AccountType): MoneyByCurrency = throw NotImplementedError()
    override suspend fun dimensionOwedByCurrency(dimensionId: Long): MoneyByCurrency = throw NotImplementedError()
    override suspend fun dimensionFlowsByCurrency(dimensionId: Long): DimensionFlowsByCurrency = throw NotImplementedError()
    override suspend fun owedByDimensionByCurrency(dimensionIds: Collection<Long>): Map<Long, MoneyByCurrency> = throw NotImplementedError()
    override suspend fun flowsByDimensionByCurrency(dimensionIds: Collection<Long>): Map<Long, DimensionFlowsByCurrency> = throw NotImplementedError()
    override suspend fun liabilityMonthFlowsByCurrency(month: YearMonth): LiabilityMonthFlowsByCurrency = throw NotImplementedError()
    override suspend fun assetMonthFlowsByCurrency(month: YearMonth): AssetMonthFlowsByCurrency = throw NotImplementedError()
    override suspend fun totalsByDimensionByCurrency(
        nominalType: AccountType,
        startDate: LocalDate,
        endDate: LocalDate,
        siblingAccountIds: List<Long>,
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

/** The reducer over an archive holding [rates]; the budget's own currency is the target. */
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
        override suspend fun removeAllNaming(currency: String) = Unit
    },
    getAccountCurrencies = object : GetAccountCurrenciesUseCase {
        override suspend fun invoke() = AccountCurrencies(inUse = listOf(base), ofDefaultAccount = base)
    },
)
