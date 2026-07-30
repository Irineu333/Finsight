package com.neoutils.finsight.ui.screen.dashboard

import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.CategorySpending
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.MoneyByCurrency
import com.neoutils.finsight.domain.repository.AccountFlows
import com.neoutils.finsight.domain.repository.AssetMonthFlowsByCurrency
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.repository.LiabilityMonthFlowsByCurrency
import com.neoutils.finsight.domain.usecase.CalculateBalanceUseCase
import com.neoutils.finsight.domain.usecase.CalculateBudgetProgressUseCase
import com.neoutils.finsight.domain.usecase.CalculateCategoryIncomeUseCase
import com.neoutils.finsight.domain.usecase.CalculateCategorySpendingUseCase
import com.neoutils.finsight.domain.usecase.GetPendingRecurringUseCase
import com.neoutils.finsight.feature.shell.api.NavCatalog
import com.neoutils.finsight.feature.shell.api.NavDestination
import com.neoutils.finsight.ui.mapper.InvoiceUiMapper
import com.neoutils.finsight.ui.model.InvoiceUi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import com.neoutils.finsight.domain.repository.DimensionFlowsByCurrency
import com.neoutils.finsight.domain.repository.ScopeStatsByCurrency

/**
 * The neutral perimeter (`ASSET` + `LIABILITY`) is derived by *summing* the two per-nature
 * flow reads the ledger already exposes — never by a third aggregate (design D2). What is
 * pinned here is the arithmetic that makes the three flow widgets readable side by side,
 * and that the sum is **per currency**: each summed with its own, never converted.
 */
class DashboardOverallBalanceStatsTest {

    private val march = YearMonth(2026, 3)

    private fun builder(
        asset: AssetMonthFlowsByCurrency,
        liability: LiabilityMonthFlowsByCurrency,
    ) = DashboardComponentsBuilder(
        calculateBalanceUseCase = CalculateBalanceUseCase(entryRepository = FlowsEntryRepository(asset, liability)),
        calculateCategorySpendingUseCase = object : CalculateCategorySpendingUseCase {
            override suspend fun invoke(forYearMonth: YearMonth): List<CategorySpending> = throw NotImplementedError()
        },
        calculateCategoryIncomeUseCase = object : CalculateCategoryIncomeUseCase {
            override suspend fun invoke(forYearMonth: YearMonth): List<CategorySpending> = throw NotImplementedError()
        },
        calculateBudgetProgressUseCase = CalculateBudgetProgressUseCase(FlowsEntryRepository(asset, liability), reducer()),
        getPendingRecurringUseCase = GetPendingRecurringUseCase(),
        invoiceUiMapper = object : InvoiceUiMapper {
            override suspend fun toUi(invoice: Invoice, cardInvoices: List<Invoice>): InvoiceUi =
                throw NotImplementedError()
        },
        entryRepository = FlowsEntryRepository(asset, liability),
        accountRepository = FakeAccountRepository(),
        consolidateMoney = reducer(),
        navCatalog = object : NavCatalog { override val destinations: List<NavDestination> = emptyList() },
    )

    private fun input() = DashboardComponentsInput(
        transactions = emptyList(),
        creditCards = emptyList(),
        invoicesByCreditCardId = emptyMap(),
        accounts = emptyList(),
        budgets = emptyList(),
        recurringList = emptyList(),
        occurrences = emptyList(),
        today = LocalDate(2026, 3, 20),
        targetMonth = march,
    )

    private suspend fun build(
        key: String,
        asset: AssetMonthFlowsByCurrency,
        liability: LiabilityMonthFlowsByCurrency,
        config: Map<String, String> = emptyMap(),
    ) = builder(asset, liability).build(
        key = key,
        input = input(),
        context = DashboardBuilderContext(pendingRecurring = emptyList()),
        config = config,
    )

    private fun brl(value: Double) = MoneyByCurrency.of("BRL", value)

    // A card purchase lands only on the LIABILITY leg — the two expense sets are disjoint,
    // so summing them counts it exactly once.
    private val assetFlows = AssetMonthFlowsByCurrency(
        income = brl(1000.0),
        expense = brl(300.0),
        adjustment = MoneyByCurrency.zero,
    )
    private val liabilityFlows = LiabilityMonthFlowsByCurrency(
        expense = brl(250.0),
        payment = brl(400.0),
        adjustment = MoneyByCurrency.zero,
    )

    private suspend fun overall(
        asset: AssetMonthFlowsByCurrency = assetFlows,
        liability: LiabilityMonthFlowsByCurrency = liabilityFlows,
        config: Map<String, String> = emptyMap(),
    ) = build(DashboardComponentType.OVERALL_BALANCE_STATS.key, asset, liability, config)
        as? DashboardComponent.OverallBalanceStats

    @Test
    fun `the neutral expense sums both natures, counting a card purchase once`() = runTest {
        assertEquals(550.0, overall()!!.expense.value)
    }

    @Test
    fun `an invoice payment is not expense in the neutral perimeter`() = runTest {
        // Both legs of a payment sit inside the perimeter, so it is internal movement:
        // doubling the payment must not move the expense by a cent.
        val doubledPayment = liabilityFlows.copy(payment = brl(800.0))
        assertEquals(overall()!!.expense.value, overall(liability = doubledPayment)!!.expense.value)
    }

    @Test
    fun `the neutral income equals the accounts income`() = runTest {
        val accounts = build(DashboardComponentType.CONCRETE_BALANCE_STATS.key, assetFlows, liabilityFlows)
            as DashboardComponent.ConcreteBalanceStats

        assertEquals(accounts.income.value, overall()!!.income.value)
    }

    @Test
    fun `the neutral expense is the accounts expense plus the card spending`() = runTest {
        val accounts = build(DashboardComponentType.CONCRETE_BALANCE_STATS.key, assetFlows, liabilityFlows)
            as DashboardComponent.ConcreteBalanceStats
        val card = build(DashboardComponentType.CREDIT_CARD_BALANCE_STATS.key, assetFlows, liabilityFlows)
            as DashboardComponent.CreditCardBalanceStats

        assertEquals(accounts.expense.value + card.expense.value, overall()!!.expense.value)
    }

    @Test
    fun `a month with no movement keeps the neutral widget by default`() = runTest {
        val component = overall(
            asset = AssetMonthFlowsByCurrency.zero,
            liability = LiabilityMonthFlowsByCurrency.zero,
        )

        assertNotNull(component)
        assertEquals(0.0, component.income.value)
        assertEquals(0.0, component.expense.value)
    }

    /**
     * **Two currencies, each summed with its own.** Account expense in reais and card
     * expense in dollars are two facts, and the neutral perimeter is both of them — not
     * one number that added them. With no rate in the archive the figure keeps a term
     * each, which is what the reducer does when it cannot reduce (design D9).
     */
    @Test
    fun `two currencies are each summed with their own and neither is converted`() = runTest {
        val component = overall(
            asset = assetFlows.copy(expense = brl(300.0)),
            liability = liabilityFlows.copy(expense = MoneyByCurrency.of("USD", 50.0)),
        )!!

        val terms = component.expense.terms.associate { it.currency to it.value }

        assertEquals(mapOf("BRL" to 300.0, "USD" to 50.0), terms)
        assertEquals(true, component.expense.isApproximate, "more than one currency went in")
    }

    /** And with a shared currency on both sides, the two do add — into that one currency. */
    @Test
    fun `the same currency on both sides adds into one term`() = runTest {
        val component = overall(
            asset = assetFlows.copy(expense = MoneyByCurrency.of("USD", 20.0)),
            liability = liabilityFlows.copy(expense = MoneyByCurrency.of("USD", 30.0)),
        )!!

        assertEquals(50.0, component.expense.value)
        assertEquals("USD", component.expense.terms.single().currency)
        assertEquals(false, component.expense.isApproximate, "one currency, nothing reconciled")
    }
}

private class FlowsEntryRepository(
    private val asset: AssetMonthFlowsByCurrency,
    private val liability: LiabilityMonthFlowsByCurrency,
) : IEntryRepository {
    override suspend fun assetMonthFlowsByCurrency(month: YearMonth) = asset
    override suspend fun liabilityMonthFlowsByCurrency(month: YearMonth) = liability

    override suspend fun getEntriesByTransaction(transactionId: Long): List<Entry> = throw NotImplementedError()
    override fun observeEntriesByTransaction(transactionId: Long): Flow<List<Entry>> = throw NotImplementedError()
    override fun observeLedgerChanges(): Flow<Unit> = flowOf(Unit)
    override suspend fun balance(accountId: Long): Double = throw NotImplementedError()
    override suspend fun hasEntries(accountId: Long): Boolean = false
    override suspend fun hasEntriesForDimension(dimensionId: Long): Boolean = false
    override suspend fun accountFlows(month: YearMonth, accountId: Long): AccountFlows = throw NotImplementedError()
    override suspend fun dimensionEntryCountInMonth(month: YearMonth, dimensionId: Long): Int = throw NotImplementedError()

    override suspend fun accountBalanceUpTo(accountId: Long, target: YearMonth): Double = throw NotImplementedError()
    override suspend fun balanceUpToByCurrency(target: YearMonth): MoneyByCurrency = throw NotImplementedError()
    override suspend fun naturalBalanceUpToByCurrency(target: YearMonth, type: AccountType): MoneyByCurrency = throw NotImplementedError()
    override suspend fun dimensionBalanceInMonthByCurrency(month: YearMonth, dimensionId: Long): MoneyByCurrency = throw NotImplementedError()
    override suspend fun dimensionOwedByCurrency(dimensionId: Long): MoneyByCurrency = throw NotImplementedError()
    override suspend fun dimensionFlowsByCurrency(dimensionId: Long): DimensionFlowsByCurrency = throw NotImplementedError()
    override suspend fun owedByDimensionByCurrency(dimensionIds: Collection<Long>): Map<Long, MoneyByCurrency> = throw NotImplementedError()
    override suspend fun flowsByDimensionByCurrency(dimensionIds: Collection<Long>): Map<Long, DimensionFlowsByCurrency> = throw NotImplementedError()
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
