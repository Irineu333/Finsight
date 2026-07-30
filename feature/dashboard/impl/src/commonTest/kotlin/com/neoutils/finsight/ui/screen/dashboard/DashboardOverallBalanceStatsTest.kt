package com.neoutils.finsight.ui.screen.dashboard

import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.CategorySpending
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.repository.AccountFlows
import com.neoutils.finsight.domain.repository.AssetMonthFlows
import com.neoutils.finsight.domain.repository.DimensionFlows
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.repository.LiabilityMonthFlows
import com.neoutils.finsight.domain.repository.ScopeStats
import com.neoutils.finsight.domain.usecase.CalculateBalanceUseCase
import com.neoutils.finsight.domain.usecase.CalculateBudgetProgressUseCase
import com.neoutils.finsight.domain.usecase.CalculateCategoryIncomeUseCase
import com.neoutils.finsight.domain.usecase.CalculateCategorySpendingUseCase
import com.neoutils.finsight.domain.usecase.GetPendingRecurringUseCase
import com.neoutils.finsight.feature.shell.api.NavCatalog
import com.neoutils.finsight.feature.shell.api.NavDestination
import com.neoutils.finsight.test.StubEntryRepository
import com.neoutils.finsight.test.brl
import com.neoutils.finsight.ui.mapper.InvoiceUiMapper
import com.neoutils.finsight.ui.model.InvoiceUi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth

/**
 * The neutral perimeter (`ASSET` + `LIABILITY`) is derived by *summing* the two per-nature
 * flow reads the ledger already exposes — never by a third aggregate (design D2). What is
 * pinned here is the arithmetic that makes the three flow widgets readable side by side.
 */
class DashboardOverallBalanceStatsTest {

    private val march = YearMonth(2026, 3)

    private fun builder(
        asset: AssetMonthFlows,
        liability: LiabilityMonthFlows,
    ) = DashboardComponentsBuilder(
        calculateBalanceUseCase = CalculateBalanceUseCase(entryRepository = FlowsEntryRepository(asset, liability)),
        calculateCategorySpendingUseCase = object : CalculateCategorySpendingUseCase {
            override suspend fun invoke(forYearMonth: YearMonth): List<CategorySpending> = throw NotImplementedError()
        },
        calculateCategoryIncomeUseCase = object : CalculateCategoryIncomeUseCase {
            override suspend fun invoke(forYearMonth: YearMonth): List<CategorySpending> = throw NotImplementedError()
        },
        calculateBudgetProgressUseCase = CalculateBudgetProgressUseCase(FlowsEntryRepository(asset, liability)),
        getPendingRecurringUseCase = GetPendingRecurringUseCase(),
        invoiceUiMapper = object : InvoiceUiMapper {
            override suspend fun toUi(invoice: Invoice, cardInvoices: List<Invoice>): InvoiceUi =
                throw NotImplementedError()
        },
        entryRepository = FlowsEntryRepository(asset, liability),
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
        asset: AssetMonthFlows,
        liability: LiabilityMonthFlows,
        config: Map<String, String> = emptyMap(),
    ) = builder(asset, liability).build(
        key = key,
        input = input(),
        context = DashboardBuilderContext(pendingRecurring = emptyList()),
        config = config,
    )

    // A card purchase lands only on the LIABILITY leg — the two expense sets are disjoint,
    // so summing them counts it exactly once.
    private val assetFlows = AssetMonthFlows(income = brl(1000.0), expense = brl(300.0), adjustment = brl(0.0))
    private val liabilityFlows = LiabilityMonthFlows(expense = brl(250.0), payment = brl(400.0), adjustment = brl(0.0))

    private suspend fun overall(
        asset: AssetMonthFlows = assetFlows,
        liability: LiabilityMonthFlows = liabilityFlows,
        config: Map<String, String> = emptyMap(),
    ) = build(DashboardComponentType.OVERALL_BALANCE_STATS.key, asset, liability, config)
        as? DashboardComponent.OverallBalanceStats

    @Test
    fun `the neutral expense sums both natures, counting a card purchase once`() = runTest {
        assertEquals(550.0, overall()!!.expense.primary.value)
    }

    @Test
    fun `an invoice payment is not expense in the neutral perimeter`() = runTest {
        // Both legs of a payment sit inside the perimeter, so it is internal movement:
        // doubling the payment must not move the expense by a cent.
        val doubledPayment = liabilityFlows.copy(payment = liabilityFlows.payment + liabilityFlows.payment)
        assertEquals(overall()!!.expense.primary.value, overall(liability = doubledPayment)!!.expense.primary.value)
    }

    @Test
    fun `the neutral income equals the accounts income`() = runTest {
        val accounts = build(DashboardComponentType.CONCRETE_BALANCE_STATS.key, assetFlows, liabilityFlows)
            as DashboardComponent.ConcreteBalanceStats

        assertEquals(accounts.income.primary.value, overall()!!.income.primary.value)
    }

    @Test
    fun `the neutral expense is the accounts expense plus the card spending`() = runTest {
        val accounts = build(DashboardComponentType.CONCRETE_BALANCE_STATS.key, assetFlows, liabilityFlows)
            as DashboardComponent.ConcreteBalanceStats
        val card = build(DashboardComponentType.CREDIT_CARD_BALANCE_STATS.key, assetFlows, liabilityFlows)
            as DashboardComponent.CreditCardBalanceStats

        assertEquals(accounts.expense.primary.value + card.expense.primary.value, overall()!!.expense.primary.value)
    }

    @Test
    fun `a month with no movement keeps the neutral widget by default`() = runTest {
        val component = overall(
            asset = AssetMonthFlows(income = brl(0.0), expense = brl(0.0), adjustment = brl(0.0)),
            liability = LiabilityMonthFlows(expense = brl(0.0), payment = brl(0.0), adjustment = brl(0.0)),
        )

        assertNotNull(component)
        assertEquals(0.0, component.income.primary.value)
        assertEquals(0.0, component.expense.primary.value)
    }
}

private class FlowsEntryRepository(
    private val asset: AssetMonthFlows,
    private val liability: LiabilityMonthFlows,
) : StubEntryRepository() {
    override suspend fun assetMonthFlows(month: YearMonth): AssetMonthFlows = asset
    override suspend fun liabilityMonthFlows(month: YearMonth): LiabilityMonthFlows = liability

    override fun observeLedgerChanges(): Flow<Unit> = flowOf(Unit)
    override suspend fun hasEntries(accountId: Long): Boolean = false
    override suspend fun hasEntriesForDimension(dimensionId: Long): Boolean = false
}
