package com.neoutils.finsight.ui.screen.dashboard

import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.CategorySpending
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.MoneyByCurrency
import com.neoutils.finsight.domain.model.SpendingSubject
import com.neoutils.finsight.domain.repository.AccountFlows
import com.neoutils.finsight.domain.repository.AssetMonthFlowsByCurrency
import com.neoutils.finsight.domain.repository.DimensionFlowsByCurrency
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.repository.LiabilityMonthFlowsByCurrency
import com.neoutils.finsight.domain.repository.ScopeStatsByCurrency
import com.neoutils.finsight.domain.usecase.CalculateBalanceUseCase
import com.neoutils.finsight.domain.usecase.CalculateBudgetProgressUseCase
import com.neoutils.finsight.domain.usecase.CalculateCategoryIncomeUseCase
import com.neoutils.finsight.domain.usecase.CalculateCategorySpendingUseCase
import com.neoutils.finsight.domain.usecase.GetPendingRecurringUseCase
import com.neoutils.finsight.extension.ConsolidatedAmount
import com.neoutils.finsight.extension.DisplayAmount
import com.neoutils.finsight.feature.shell.api.NavCatalog
import com.neoutils.finsight.feature.shell.api.NavDestination
import com.neoutils.finsight.ui.icons.CategoryLazyIcon
import com.neoutils.finsight.ui.model.InvoiceUi
import com.neoutils.finsight.domain.usecase.CalculateAvailableLimitUseCase
import com.neoutils.finsight.domain.usecase.Limit
import com.neoutils.finsight.ui.mapper.InvoiceUiMapper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The two breakdown widgets carry what the domain hands them, in the order it hands it —
 * the unclassified line included, and last. The builder decides nothing about a
 * breakdown, which is exactly what makes the ordering rule have a single owner.
 */
class DashboardCategoryBreakdownTest {

    private val march = YearMonth(2026, 3)

    private val food = CategorySpending(
        subject = SpendingSubject.Categorized(
            Category(
                id = 1,
                name = "Alimentação",
                icon = CategoryLazyIcon("food"),
                type = Category.Type.EXPENSE,
                createdAt = 0,
                dimensionId = 10,
            )
        ),
        amount = figure(50.0),
        percentage = 25.0,
    )

    private val unclassified = CategorySpending(
        subject = SpendingSubject.Uncategorized,
        amount = figure(150.0),
        percentage = 75.0,
    )

    private fun figure(value: Double) = ConsolidatedAmount(
        terms = listOf(DisplayAmount.magnitude(value, "BRL", isApproximate = false)),
        isApproximate = false,
        baseIndex = 0,
    )

    private fun builder(
        spending: List<CategorySpending> = emptyList(),
        income: List<CategorySpending> = emptyList(),
    ) = DashboardComponentsBuilder(
        calculateBalanceUseCase = CalculateBalanceUseCase(entryRepository = NoReadsEntryRepository),
        calculateCategorySpendingUseCase = object : CalculateCategorySpendingUseCase {
            override suspend fun invoke(forYearMonth: YearMonth): List<CategorySpending> = spending
        },
        calculateCategoryIncomeUseCase = object : CalculateCategoryIncomeUseCase {
            override suspend fun invoke(forYearMonth: YearMonth): List<CategorySpending> = income
        },
        calculateBudgetProgressUseCase = CalculateBudgetProgressUseCase(NoReadsEntryRepository, reducer()),
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
        entryRepository = NoReadsEntryRepository,
        accountRepository = FakeAccountRepository(),
        consolidateMoney = reducer(),
        navCatalog = object : NavCatalog { override val destinations: List<NavDestination> = emptyList() },
    )

    private suspend fun build(key: String, builder: DashboardComponentsBuilder) = builder.build(
        key = key,
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
        context = DashboardBuilderContext(pendingRecurring = emptyList()),
        config = emptyMap(),
    )

    @Test
    fun `the spending widget carries the unclassified line, last`() = runTest {
        val component = build(
            DashboardComponentType.SPENDING_BY_CATEGORY.key,
            builder(spending = listOf(food, unclassified)),
        ) as DashboardComponent.SpendingByCategory

        assertEquals(
            listOf(food.subject, SpendingSubject.Uncategorized),
            component.categorySpending.map { it.subject },
        )
        assertEquals(75.0, component.categorySpending.last().percentage)
    }

    @Test
    fun `the income widget carries its own unclassified line`() = runTest {
        val component = build(
            DashboardComponentType.INCOME_BY_CATEGORY.key,
            builder(income = listOf(unclassified)),
        ) as DashboardComponent.IncomeByCategory

        assertEquals(listOf(SpendingSubject.Uncategorized), component.categoryIncome.map { it.subject })
    }

    @Test
    fun `a fully classified month carries no unclassified line`() = runTest {
        val component = build(
            DashboardComponentType.SPENDING_BY_CATEGORY.key,
            builder(spending = listOf(food)),
        ) as DashboardComponent.SpendingByCategory

        assertEquals(listOf(food.subject), component.categorySpending.map { it.subject })
    }
}

/** The breakdown arrives from the use case, so the builder reads no entry of its own. */
private object NoReadsEntryRepository : IEntryRepository {
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
    override suspend fun netWorthByCurrency(): MoneyByCurrency = throw NotImplementedError()
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
