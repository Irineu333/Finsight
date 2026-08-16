package com.neoutils.finsight.ui.screen.dashboard

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.CategorySpending
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.domain.repository.AccountFlows
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.usecase.CalculateBalanceUseCase
import com.neoutils.finsight.domain.usecase.CalculateBudgetProgressUseCase
import com.neoutils.finsight.domain.usecase.CalculateCategoryIncomeUseCase
import com.neoutils.finsight.domain.usecase.CalculateCategorySpendingUseCase
import com.neoutils.finsight.domain.usecase.GetPendingRecurringUseCase
import com.neoutils.finsight.feature.shell.api.NavCatalog
import com.neoutils.finsight.feature.shell.api.NavDestination
import com.neoutils.finsight.domain.usecase.CalculateAvailableLimitUseCase
import com.neoutils.finsight.domain.usecase.Limit
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
import com.neoutils.finsight.domain.model.MoneyByCurrency
import com.neoutils.finsight.domain.repository.AssetMonthFlowsByCurrency
import com.neoutils.finsight.domain.repository.DimensionFlowsByCurrency
import com.neoutils.finsight.domain.repository.LiabilityMonthFlowsByCurrency
import com.neoutils.finsight.domain.repository.ScopeStatsByCurrency

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
        calculateBudgetProgressUseCase = CalculateBudgetProgressUseCase(NoFlowsEntryRepository, reducer()),
        getPendingRecurringUseCase = GetPendingRecurringUseCase(),
        invoiceUiMapper = object : InvoiceUiMapper {
            override suspend fun toUi(
                invoice: Invoice,
                cardInvoices: List<Invoice>,
                limit: Limit,
            ): InvoiceUi = throw NotImplementedError()
        },
        calculateAvailableLimit = object : CalculateAvailableLimitUseCase {
            override suspend fun invoke(creditCardIds: Collection<Long>): Map<Long, Limit> =
                emptyMap()
        },
        entryRepository = NoFlowsEntryRepository,
        accountRepository = FakeAccountRepository(),
        consolidateMoney = reducer(),
        navCatalog = object : NavCatalog { override val destinations: List<NavDestination> = emptyList() },
    )

    // A template is denominated by the account it names (design D17). One with no
    // account names nothing, and a figure nobody can denominate is left out — so the
    // fixture gives it one, which is also what the real screen always has.
    private val account = Account(id = 1, name = "Nubank", type = AccountType.ASSET, currency = "BRL")

    private fun recurring(type: TransactionType, amount: Double) = Recurring(
        id = amount.toLong(),
        type = type,
        amount = amount,
        title = null,
        dayOfMonth = 5,
        category = null,
        account = account,
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
    fun `a month with only income keeps the expense class at zero`() = runTest {
        val component = pending(listOf(recurring(TransactionType.INCOME, 1200.0)))

        assertNotNull(component)
        assertEquals(1200.0, component.pendingIncome.value)
        assertEquals(0.0, component.pendingExpense.value)
    }

    @Test
    fun `a month with only expense keeps the income class at zero`() = runTest {
        val component = pending(listOf(recurring(TransactionType.EXPENSE, 350.0)))

        assertNotNull(component)
        assertEquals(0.0, component.pendingIncome.value)
        assertEquals(350.0, component.pendingExpense.value)
    }

    @Test
    fun `an empty month keeps both classes when the widget is not set to hide`() = runTest {
        val component = pending(emptyList())

        assertNotNull(component)
        assertEquals(0.0, component.pendingIncome.value)
        assertEquals(0.0, component.pendingExpense.value)
    }

    @Test
    fun `hiding when empty removes the whole widget and never a single class`() = runTest {
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
        assertEquals(1200.0, component.pendingIncome.value)
        assertEquals(0.0, component.pendingExpense.value)
    }
}

private object NoFlowsEntryRepository : IEntryRepository {
    override suspend fun getEntriesByTransaction(transactionId: Long): List<Entry> = throw NotImplementedError()
    override fun observeEntriesByTransaction(transactionId: Long): Flow<List<Entry>> = throw NotImplementedError()
    override fun observeLedgerChanges(): Flow<Unit> = flowOf(Unit)
    override suspend fun balance(accountId: Long): Double = throw NotImplementedError()
    override suspend fun hasEntries(accountId: Long): Boolean = false
    override suspend fun hasEntriesForDimension(dimensionId: Long): Boolean = false
    override suspend fun accountFlows(month: YearMonth, accountId: Long, yieldDimensionId: Long?): AccountFlows = throw NotImplementedError()
    override suspend fun dimensionEntryCountInMonth(month: YearMonth, dimensionId: Long): Int = throw NotImplementedError()

    override suspend fun accountBalanceUpTo(accountId: Long, target: LocalDate): Double = throw NotImplementedError()
    override suspend fun balanceUpToByCurrency(target: YearMonth, excludedAccountIds: Set<Long>): MoneyByCurrency = throw NotImplementedError()
    override suspend fun naturalBalanceUpToByCurrency(target: YearMonth, type: AccountType, excludedAccountIds: Set<Long>): MoneyByCurrency = throw NotImplementedError()
    override suspend fun dimensionBalanceInMonthByCurrency(month: YearMonth, dimensionId: Long): MoneyByCurrency = throw NotImplementedError()
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
