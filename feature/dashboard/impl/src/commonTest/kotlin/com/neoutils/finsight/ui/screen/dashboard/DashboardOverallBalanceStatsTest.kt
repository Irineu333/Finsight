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
    private val assetFlows = AssetMonthFlows(income = 1000.0, yield = 0.0, expense = 300.0, adjustment = 0.0)
    private val liabilityFlows = LiabilityMonthFlows(expense = 250.0, payment = 400.0, adjustment = 0.0)

    private suspend fun overall(
        asset: AssetMonthFlows = assetFlows,
        liability: LiabilityMonthFlows = liabilityFlows,
        config: Map<String, String> = emptyMap(),
    ) = build(DashboardComponentType.OVERALL_BALANCE_STATS.key, asset, liability, config)
        as? DashboardComponent.OverallBalanceStats

    @Test
    fun `the neutral expense sums both natures, counting a card purchase once`() = runTest {
        assertEquals(550.0, overall()!!.expense)
    }

    @Test
    fun `an invoice payment is not expense in the neutral perimeter`() = runTest {
        // Both legs of a payment sit inside the perimeter, so it is internal movement:
        // doubling the payment must not move the expense by a cent.
        val doubledPayment = liabilityFlows.copy(payment = liabilityFlows.payment * 2)
        assertEquals(overall()!!.expense, overall(liability = doubledPayment)!!.expense)
    }

    @Test
    fun `the neutral income equals the accounts income`() = runTest {
        val accounts = build(DashboardComponentType.CONCRETE_BALANCE_STATS.key, assetFlows, liabilityFlows)
            as DashboardComponent.ConcreteBalanceStats

        assertEquals(accounts.income, overall()!!.income)
    }

    @Test
    fun `the neutral expense is the accounts expense plus the card spending`() = runTest {
        val accounts = build(DashboardComponentType.CONCRETE_BALANCE_STATS.key, assetFlows, liabilityFlows)
            as DashboardComponent.ConcreteBalanceStats
        val card = build(DashboardComponentType.CREDIT_CARD_BALANCE_STATS.key, assetFlows, liabilityFlows)
            as DashboardComponent.CreditCardBalanceStats

        assertEquals(accounts.expense + card.expense, overall()!!.expense)
    }

    @Test
    fun `a month with no movement keeps the neutral widget by default`() = runTest {
        val component = overall(
            asset = AssetMonthFlows(income = 0.0, yield = 0.0, expense = 0.0, adjustment = 0.0),
            liability = LiabilityMonthFlows(expense = 0.0, payment = 0.0, adjustment = 0.0),
        )

        assertNotNull(component)
        assertEquals(0.0, component.income)
        assertEquals(0.0, component.expense)
    }
}

private class FlowsEntryRepository(
    private val asset: AssetMonthFlows,
    private val liability: LiabilityMonthFlows,
) : IEntryRepository {
    override suspend fun assetMonthFlows(month: YearMonth, yieldDimensionId: Long?): AssetMonthFlows = asset
    override suspend fun liabilityMonthFlows(month: YearMonth): LiabilityMonthFlows = liability

    override suspend fun getEntriesByTransaction(transactionId: Long): List<Entry> = throw NotImplementedError()
    override fun observeEntriesByTransaction(transactionId: Long): Flow<List<Entry>> = throw NotImplementedError()
    override fun observeLedgerChanges(): Flow<Unit> = flowOf(Unit)
    override suspend fun balanceUpTo(target: YearMonth, accountId: Long?): Double = throw NotImplementedError()
    override suspend fun naturalBalanceUpTo(target: YearMonth, type: AccountType): Double = throw NotImplementedError()
    override suspend fun balance(accountId: Long): Double = throw NotImplementedError()
    override suspend fun hasEntries(accountId: Long): Boolean = false
    override suspend fun hasEntriesForDimension(dimensionId: Long): Boolean = false
    override suspend fun dimensionBalanceInMonth(month: YearMonth, dimensionId: Long): Double = throw NotImplementedError()
    override suspend fun accountFlows(month: YearMonth, accountId: Long, yieldDimensionId: Long?): AccountFlows = throw NotImplementedError()
    override suspend fun dimensionEntryCountInMonth(month: YearMonth, dimensionId: Long): Int = throw NotImplementedError()
    override suspend fun dimensionOwed(dimensionId: Long): Double = throw NotImplementedError()
    override suspend fun dimensionFlows(dimensionId: Long): DimensionFlows = throw NotImplementedError()
    override suspend fun netWorth(): Double = throw NotImplementedError()
    override suspend fun totalsByDimension(nominalType: AccountType, startDate: LocalDate, endDate: LocalDate, siblingAccountIds: List<Long>): Map<Long?, Double> = throw NotImplementedError()
    override suspend fun totalsByDimensionInScope(nominalType: AccountType, scopeDimensionIds: List<Long>): Map<Long?, Double> = throw NotImplementedError()
    override suspend fun scopeStats(scopeAccountIds: List<Long>, startDate: LocalDate, endDate: LocalDate): ScopeStats = throw NotImplementedError()
}
