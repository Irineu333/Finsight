package com.neoutils.finsight.ui.screen.dashboard

import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.CategorySpending
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.model.TransactionType
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
import kotlin.test.assertNull

/**
 * The widget reports the same pair of classes in every month: a class that sums to zero is
 * a reading — R$ 0,00 — not an absence. What is pinned here is that the component is emitted
 * with both classes whenever it exists, so the UI has no per-card branch left to take. The
 * only binary decision is over the whole widget, and it lives in the builder.
 */
class DashboardPendingBalanceStatsTest {

    private val march = YearMonth(2026, 3)

    private val builder = DashboardComponentsBuilder(
        calculateBalanceUseCase = CalculateBalanceUseCase(entryRepository = NoFlowsEntryRepository),
        calculateCategorySpendingUseCase = object : CalculateCategorySpendingUseCase {
            override suspend fun invoke(forYearMonth: YearMonth): List<CategorySpending> = throw NotImplementedError()
        },
        calculateCategoryIncomeUseCase = object : CalculateCategoryIncomeUseCase {
            override suspend fun invoke(forYearMonth: YearMonth): List<CategorySpending> = throw NotImplementedError()
        },
        calculateBudgetProgressUseCase = CalculateBudgetProgressUseCase(NoFlowsEntryRepository),
        getPendingRecurringUseCase = GetPendingRecurringUseCase(),
        invoiceUiMapper = object : InvoiceUiMapper {
            override suspend fun toUi(invoice: Invoice, cardInvoices: List<Invoice>): InvoiceUi =
                throw NotImplementedError()
        },
        entryRepository = NoFlowsEntryRepository,
        navCatalog = object : NavCatalog { override val destinations: List<NavDestination> = emptyList() },
    )

    private fun recurring(type: TransactionType, amount: Double) = Recurring(
        id = amount.toLong(),
        type = type,
        amount = amount,
        title = null,
        dayOfMonth = 5,
        category = null,
        account = null,
        creditCard = null,
        createdAt = 0,
    )

    private suspend fun pending(
        pendingRecurring: List<Recurring>,
        config: Map<String, String> = mapOf(DashboardComponentConfig.HIDE_WHEN_EMPTY to "false"),
    ) = builder.build(
        key = DashboardComponentType.PENDING_BALANCE_STATS.key,
        input = DashboardComponentsInput(
            transactions = emptyList(),
            creditCards = emptyList(),
            invoicesByCreditCardId = emptyMap(),
            accounts = emptyList(),
            budgets = emptyList(),
            recurringList = emptyList(),
            occurrences = emptyList(),
            today = LocalDate(2026, 3, 20),
            targetMonth = march,
        ),
        context = DashboardBuilderContext(pendingRecurring = pendingRecurring),
        config = config,
    ) as? DashboardComponent.PendingBalanceStats

    @Test
    fun `a month with only income keeps the expense class, at zero`() = runTest {
        val component = pending(listOf(recurring(TransactionType.INCOME, 1200.0)))

        assertNotNull(component)
        assertEquals(1200.0, component.pendingIncome)
        assertEquals(0.0, component.pendingExpense)
    }

    @Test
    fun `a month with only expense keeps the income class, at zero`() = runTest {
        val component = pending(listOf(recurring(TransactionType.EXPENSE, 350.0)))

        assertNotNull(component)
        assertEquals(0.0, component.pendingIncome)
        assertEquals(350.0, component.pendingExpense)
    }

    @Test
    fun `an empty month keeps both classes when the widget is not set to hide`() = runTest {
        val component = pending(emptyList())

        assertNotNull(component)
        assertEquals(0.0, component.pendingIncome)
        assertEquals(0.0, component.pendingExpense)
    }

    @Test
    fun `hiding when empty removes the whole widget, never a single class`() = runTest {
        assertNull(
            pending(
                pendingRecurring = emptyList(),
                config = mapOf(DashboardComponentConfig.HIDE_WHEN_EMPTY to "true"),
            ),
        )
    }

    @Test
    fun `hiding when empty does not touch a month with a single class`() = runTest {
        val component = pending(
            pendingRecurring = listOf(recurring(TransactionType.INCOME, 1200.0)),
            config = mapOf(DashboardComponentConfig.HIDE_WHEN_EMPTY to "true"),
        )

        assertNotNull(component)
        assertEquals(1200.0, component.pendingIncome)
        assertEquals(0.0, component.pendingExpense)
    }
}

private object NoFlowsEntryRepository : IEntryRepository {
    override suspend fun assetMonthFlows(month: YearMonth): AssetMonthFlows = throw NotImplementedError()
    override suspend fun liabilityMonthFlows(month: YearMonth): LiabilityMonthFlows = throw NotImplementedError()
    override suspend fun getEntriesByTransaction(transactionId: Long): List<Entry> = throw NotImplementedError()
    override fun observeEntriesByTransaction(transactionId: Long): Flow<List<Entry>> = throw NotImplementedError()
    override fun observeLedgerChanges(): Flow<Unit> = flowOf(Unit)
    override suspend fun balanceUpTo(target: YearMonth, accountId: Long?): Double = throw NotImplementedError()
    override suspend fun naturalBalanceUpTo(target: YearMonth, type: AccountType): Double = throw NotImplementedError()
    override suspend fun balance(accountId: Long): Double = throw NotImplementedError()
    override suspend fun hasEntries(accountId: Long): Boolean = false
    override suspend fun hasEntriesForDimension(dimensionId: Long): Boolean = false
    override suspend fun dimensionBalanceInMonth(month: YearMonth, dimensionId: Long): Double = throw NotImplementedError()
    override suspend fun accountFlows(month: YearMonth, accountId: Long): AccountFlows = throw NotImplementedError()
    override suspend fun dimensionEntryCountInMonth(month: YearMonth, dimensionId: Long): Int = throw NotImplementedError()
    override suspend fun dimensionOwed(dimensionId: Long): Double = throw NotImplementedError()
    override suspend fun dimensionFlows(dimensionId: Long): DimensionFlows = throw NotImplementedError()
    override suspend fun totalsByDimension(nominalType: AccountType, startDate: LocalDate, endDate: LocalDate, siblingAccountIds: List<Long>): Map<Long?, Double> = throw NotImplementedError()
    override suspend fun totalsByDimensionInScope(nominalType: AccountType, scopeDimensionIds: List<Long>): Map<Long?, Double> = throw NotImplementedError()
    override suspend fun scopeStats(scopeAccountIds: List<Long>, startDate: LocalDate, endDate: LocalDate): ScopeStats = throw NotImplementedError()
}
