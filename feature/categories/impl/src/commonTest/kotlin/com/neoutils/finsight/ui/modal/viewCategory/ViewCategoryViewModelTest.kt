@file:OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)

package com.neoutils.finsight.ui.modal.viewCategory

import com.neoutils.finsight.RecordingAnalytics
import app.cash.turbine.test
import app.cash.turbine.turbineScope
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.exception.DetailNotFoundException
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.CategoryOverview
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.model.ExchangeRate
import com.neoutils.finsight.domain.model.Budget
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.model.SpendingVariation
import com.neoutils.finsight.domain.repository.AccountFlows
import com.neoutils.finsight.domain.repository.IBaseCurrencyRepository
import com.neoutils.finsight.domain.repository.IBudgetRepository
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.repository.IExchangeRateRepository
import com.neoutils.finsight.domain.repository.IRecurringRepository
import com.neoutils.finsight.domain.usecase.AccountCurrencies
import com.neoutils.finsight.domain.usecase.CalculateCategoryOverviewUseCase
import com.neoutils.finsight.domain.usecase.ConsolidateMoneyUseCase
import com.neoutils.finsight.domain.usecase.GetAccountCurrenciesUseCase
import com.neoutils.finsight.domain.usecase.ObserveConsolidationChangesUseCase
import com.neoutils.finsight.domain.usecase.ResolveCategoryRetirabilityUseCase
import com.neoutils.finsight.domain.usecase.UnarchiveCategoryUseCase
import com.neoutils.finsight.ui.model.RetireAction
import com.neoutils.finsight.extension.currentYearMonth
import com.neoutils.finsight.ui.icons.CategoryLazyIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.minusMonth
import kotlinx.datetime.plusMonth
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import com.neoutils.finsight.domain.model.MoneyByCurrency
import com.neoutils.finsight.domain.model.RecurringOccurrence
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionIntent
import com.neoutils.finsight.domain.repository.AssetMonthFlowsByCurrency
import com.neoutils.finsight.domain.repository.DimensionFlowsByCurrency
import com.neoutils.finsight.domain.repository.LiabilityMonthFlowsByCurrency
import com.neoutils.finsight.domain.repository.ScopeStatsByCurrency

/**
 * What the detail states, and what it no longer offers.
 *
 * The window's own arithmetic is pinned in `CalculateCategoryOverviewUseCaseTest`, where
 * it lives; here the claim is that this view model observes it, redraws when the ledger
 * moves, and decides nothing itself — which is why the real use case is wired in rather
 * than a stub of it.
 */
class ViewCategoryViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    // Midday mid-month: every zone reads the same month from it.
    private val clock = object : Clock {
        override fun now(): Instant = Instant.parse("2026-06-15T12:00:00Z")
    }
    private val currentMonth = clock.currentYearMonth()

    private class FakeCrashlytics : Crashlytics {
        val recorded = mutableListOf<Throwable>()
        override fun setUserId(id: String?) = Unit
        override fun recordException(e: Throwable) { recorded += e }
    }

    private class FakeCategoryRepository : ICategoryRepository {
        private val byId = MutableSharedFlow<Category?>(replay = 1)
        val unarchived = mutableListOf<Long>()
        fun emit(category: Category?) { byId.tryEmit(category) }
        override fun observeCategoryById(id: Long): Flow<Category?> = byId
        override fun observeAllCategories(): Flow<List<Category>> = throw NotImplementedError()
        override suspend fun getAllCategories(): List<Category> = throw NotImplementedError()
        override suspend fun getAllCategoriesIncludingClosed(): List<Category> = getAllCategories()
        override fun observeAllCategoriesIncludingClosed(): Flow<List<Category>> = observeAllCategories()
        override fun observeCategoriesByType(type: Category.Type): Flow<List<Category>> = throw NotImplementedError()
        override suspend fun getCategoryById(id: Long): Category? = throw NotImplementedError()
        override suspend fun getCategoryBySystemKey(systemKey: String): Category? = null
        override suspend fun getCategoryByDimensionId(dimensionId: Long): Category? = null
        override suspend fun archive(id: Long) = Unit
        override suspend fun unarchive(id: Long) { unarchived += id }
        override suspend fun existsByName(name: String, ignoreId: Long): Boolean = false

        override suspend fun insert(category: Category) = throw NotImplementedError()
        override suspend fun insertAll(categories: List<Category>) = throw NotImplementedError()
        override suspend fun update(category: Category) = throw NotImplementedError()
        override suspend fun delete(category: Category) = throw NotImplementedError()
    }

    /**
     * The ledger reader: one dimension's monthly series, honouring the upper cut where
     * production honours it. The month- and dimension-filtering correctness lives in SQL
     * (`EntryDao`, covered by the ledger's own query tests).
     */
    private class FakeEntryRepository(
        var series: Map<Long, Map<YearMonth, Double>> = emptyMap(),
    ) : IEntryRepository {
        /** Stands in for Room's invalidation: emit after moving the ledger. */
        val ledger = MutableSharedFlow<Unit>(replay = 1).also { it.tryEmit(Unit) }

        override suspend fun dimensionMonthlySeriesByCurrency(
            dimensionId: Long,
            upTo: YearMonth,
        ): Map<YearMonth, MoneyByCurrency> = series[dimensionId]
            .orEmpty()
            .filterKeys { it <= upTo }
            .mapValues { (_, value) -> MoneyByCurrency.of("BRL", value) }

        override suspend fun dimensionBalanceInMonthByCurrency(month: YearMonth, dimensionId: Long) = throw NotImplementedError()
        override suspend fun balance(accountId: Long): Double = throw NotImplementedError()
        override suspend fun hasEntries(accountId: Long): Boolean = false
        override suspend fun hasEntriesForDimension(dimensionId: Long): Boolean = false
        override suspend fun dimensionEntryCountInMonth(month: YearMonth, dimensionId: Long): Int = throw NotImplementedError()
        override suspend fun getEntriesByTransaction(transactionId: Long): List<Entry> = throw NotImplementedError()
        override fun observeEntriesByTransaction(transactionId: Long): Flow<List<Entry>> = throw NotImplementedError()
        override fun observeLedgerChanges(): Flow<Unit> = ledger
        override suspend fun accountFlows(month: YearMonth, accountId: Long, yieldDimensionId: Long?): AccountFlows = throw NotImplementedError()

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

    private fun category(
        id: Long = 1L,
        name: String = "Food",
        dimensionId: Long = 10L,
        isArchived: Boolean = false,
    ) = Category(
        id = id,
        name = name,
        icon = CategoryLazyIcon("shopping"),
        type = Category.Type.EXPENSE,
        createdAt = 0L,
        isArchived = isArchived,
        dimensionId = dimensionId,
    )

    private fun viewModel(
        categoryRepository: FakeCategoryRepository,
        crashlytics: FakeCrashlytics = FakeCrashlytics(),
        analytics: RecordingAnalytics = RecordingAnalytics(),
        entryRepository: FakeEntryRepository = FakeEntryRepository(),
        recurringRepository: IRecurringRepository = FakeRecurringRepository(),
        budgetRepository: IBudgetRepository = FakeBudgetRepository(),
        unarchiveCategory: UnarchiveCategoryUseCase = UnarchiveCategoryUseCase(categoryRepository),
    ) = ViewCategoryViewModel(
        categoryId = 1L,
        categoryRepository = categoryRepository,
        calculateOverview = CalculateCategoryOverviewUseCase(
            entryRepository = entryRepository,
            consolidateMoney = ConsolidateMoneyUseCase(
                baseCurrencyRepository = FakeBaseCurrencyRepository(),
                exchangeRateRepository = FakeExchangeRateRepository(),
                getAccountCurrencies = FakeAccountCurrencies(),
            ),
            clock = clock,
        ),
        resolveRetirability = ResolveCategoryRetirabilityUseCase(
            entryRepository = entryRepository,
            budgetRepository = budgetRepository,
            recurringRepository = recurringRepository,
            accountRepository = com.neoutils.finsight.domain.usecase.FakeAccounts(hasYieldingAccount = false),
        ),
        unarchiveCategory = unarchiveCategory,
        observeConsolidationChanges = ObserveConsolidationChangesUseCase(
            entryRepository = entryRepository,
            baseCurrencyRepository = FakeBaseCurrencyRepository(),
            exchangeRateRepository = FakeExchangeRateRepository(),
        ),
        analytics = analytics,
        crashlytics = crashlytics,
    )

    private class FakeBaseCurrencyRepository(base: String = "BRL") : IBaseCurrencyRepository {
        private val flow = MutableStateFlow(base)
        override fun observe(): StateFlow<String> = flow
        override suspend fun set(code: String) { flow.value = code }
    }

    private class FakeExchangeRateRepository : IExchangeRateRepository {
        override suspend fun rateAsOf(currency: String, date: LocalDate): ExchangeRate? = null
        override suspend fun ratesAsOf(date: LocalDate): Map<String, ExchangeRate> = emptyMap()
        override suspend fun rateBetween(from: String, to: String, date: LocalDate): ExchangeRate? = null
        override fun observeAll(): Flow<List<ExchangeRate>> = flowOf(emptyList())
        override suspend fun save(rate: ExchangeRate) = Unit
        override suspend fun remove(rate: ExchangeRate) = Unit
        override suspend fun countNaming(currency: String) = 0
        override suspend fun removeAllNaming(currency: String) = Unit
    }

    private class FakeRecurringRepository(private val has: Boolean = false) : IRecurringRepository {
        override suspend fun hasRecurringForCategory(categoryId: Long) = has
        override suspend fun hasTransactionForRecurring(recurringId: Long) = false
        override suspend fun hasRecurringForAccount(accountId: Long) = false
        override suspend fun hasRecurringForCreditCard(creditCardId: Long) = false
        override fun observeAllRecurring(): Flow<List<Recurring>> = flowOf(emptyList())
        override fun observeRecurringById(id: Long): Flow<Recurring?> = flowOf(null)
        override suspend fun getRecurringById(id: Long): Recurring? = null
        override suspend fun insert(recurring: Recurring) = throw NotImplementedError()
        override suspend fun createWithFirstCycle(
            recurring: Recurring,
            firstCycle: TransactionIntent,
            occurrence: RecurringOccurrence,
        ): Transaction = throw NotImplementedError()
        override suspend fun update(recurring: Recurring) = throw NotImplementedError()
        override suspend fun delete(recurring: Recurring) = throw NotImplementedError()
    }

    private class FakeBudgetRepository(private val has: Boolean = false) : IBudgetRepository {
        override suspend fun hasBudgetForCategory(categoryId: Long) = has
        override suspend fun hasBudgetForRecurring(recurringId: Long) = false
        override fun observeAllBudgets(): Flow<List<Budget>> = flowOf(emptyList())
        override suspend fun getAllBudgets(): List<Budget> = emptyList()
        override suspend fun insert(budget: Budget) = throw NotImplementedError()
        override suspend fun update(budget: Budget) = throw NotImplementedError()
        override suspend fun delete(budget: Budget) = throw NotImplementedError()
    }

    /** A series over [dimensionId], keyed by how many months back each month is. */
    private fun ledgerWith(dimensionId: Long = 10L, vararg months: Pair<Int, Double>) =
        FakeEntryRepository(
            series = mapOf(
                dimensionId to months.associate { (back, value) -> currentMonth.back(back) to value },
            ),
        )

    @Test
    fun `the state carries the current month, the window and the variation`() =
        runTest(dispatcher) {
            val repository = FakeCategoryRepository()
            // Two closed months at 100 each and 150 so far this month: the average is 100
            // and the month is 50% above it.
            val vm = viewModel(
                categoryRepository = repository,
                entryRepository = ledgerWith(months = arrayOf(2 to 100.0, 1 to 100.0, 0 to 150.0)),
            )

            vm.uiState.test {
                assertEquals(ViewCategoryUiState.Loading, awaitItem())
                repository.emit(category())
                val content = assertIs<ViewCategoryUiState.Content>(awaitItem())
                val overview = assertIs<CategoryOverview.Active>(content.overview)

                assertEquals(150.0, overview.currentMonth.amount.terms.single().value)
                assertEquals(2, checkNotNull(overview.window).months)
                assertEquals(200.0, checkNotNull(overview.window).total.terms.single().value)
                assertEquals(100.0, checkNotNull(overview.window).average.terms.single().value)
                assertEquals(0.5, assertIs<SpendingVariation.Measured>(overview.variation).fraction)
            }
        }

    @Test
    fun `an unused category with no dependents offers delete`() = runTest(dispatcher) {
        val repository = FakeCategoryRepository()
        val vm = viewModel(categoryRepository = repository)
        vm.uiState.test {
            assertEquals(ViewCategoryUiState.Loading, awaitItem())
            repository.emit(category())
            assertEquals(RetireAction.DELETE, assertIs<ViewCategoryUiState.Content>(awaitItem()).retireAction)
        }
    }

    @Test
    fun `a category still in a budget offers archive instead of delete`() = runTest(dispatcher) {
        // Deleting it would be refused (budget CASCADE), so the screen must offer the
        // action that actually works.
        val repository = FakeCategoryRepository()
        val vm = viewModel(categoryRepository = repository, budgetRepository = FakeBudgetRepository(has = true))
        vm.uiState.test {
            assertEquals(ViewCategoryUiState.Loading, awaitItem())
            repository.emit(category())
            assertEquals(RetireAction.ARCHIVE, assertIs<ViewCategoryUiState.Content>(awaitItem()).retireAction)
        }
    }

    @Test
    fun `a category a recurring points at offers archive instead of delete`() = runTest(dispatcher) {
        val repository = FakeCategoryRepository()
        val vm = viewModel(categoryRepository = repository, recurringRepository = FakeRecurringRepository(has = true))
        vm.uiState.test {
            assertEquals(ViewCategoryUiState.Loading, awaitItem())
            repository.emit(category())
            assertEquals(RetireAction.ARCHIVE, assertIs<ViewCategoryUiState.Content>(awaitItem()).retireAction)
        }
    }

    @Test
    fun `an archived category swaps the highlight for its whole history`() = runTest(dispatcher) {
        val repository = FakeCategoryRepository()
        val vm = viewModel(
            categoryRepository = repository,
            entryRepository = ledgerWith(months = arrayOf(9 to 60.0, 3 to 40.0)),
        )
        vm.uiState.test {
            assertEquals(ViewCategoryUiState.Loading, awaitItem())
            repository.emit(category(isArchived = true))
            val content = assertIs<ViewCategoryUiState.Content>(awaitItem())
            val overview = assertIs<CategoryOverview.Archived>(content.overview)

            assertTrue(content.category.isArchived)
            assertEquals(100.0, overview.total.terms.single().value)
            assertEquals(currentMonth.back(9), overview.firstMonth)
            assertEquals(currentMonth.back(3), overview.lastMonth)
        }
    }

    @Test
    fun `a non-archived category is shown active so the view offers retire`() = runTest(dispatcher) {
        val repository = FakeCategoryRepository()
        val vm = viewModel(categoryRepository = repository)
        vm.uiState.test {
            assertEquals(ViewCategoryUiState.Loading, awaitItem())
            repository.emit(category(isArchived = false))
            assertFalse(assertIs<ViewCategoryUiState.Content>(awaitItem()).category.isArchived)
        }
    }

    @Test
    fun `the unarchive action unarchives the shown category`() = runTest(dispatcher) {
        val repository = FakeCategoryRepository()
        val analytics = RecordingAnalytics()
        val vm = viewModel(categoryRepository = repository, analytics = analytics)
        vm.uiState.test {
            assertEquals(ViewCategoryUiState.Loading, awaitItem())
            repository.emit(category(id = 5L, isArchived = true))
            assertIs<ViewCategoryUiState.Content>(awaitItem())

            vm.onAction(ViewCategoryAction.Unarchive)
            runCurrent()

            assertEquals(listOf(5L), repository.unarchived)
            assertEquals(listOf("unarchive_category"), analytics.events.map { it.name })
        }
    }

    @Test
    fun `the figures refresh when the ledger moves without the category changing`() =
        runTest(dispatcher) {
            // The figures are SQL aggregates, so nothing about the category row changes
            // when a transaction is written. Without a ledger signal the screen kept
            // showing the old total while the ledger had already moved.
            val repository = FakeCategoryRepository()
            val entries = ledgerWith(months = arrayOf(1 to 100.0, 0 to 42.5))
            val vm = viewModel(categoryRepository = repository, entryRepository = entries)

            vm.uiState.test {
                assertEquals(ViewCategoryUiState.Loading, awaitItem())
                repository.emit(category())
                val first = assertIs<CategoryOverview.Active>(
                    assertIs<ViewCategoryUiState.Content>(awaitItem()).overview,
                )
                assertEquals(42.5, first.currentMonth.amount.terms.single().value)

                entries.series = mapOf(
                    10L to mapOf(currentMonth.back(1) to 100.0, currentMonth to 60.0),
                )
                entries.ledger.emit(Unit)

                val refreshed = assertIs<CategoryOverview.Active>(
                    assertIs<ViewCategoryUiState.Content>(awaitItem()).overview,
                )
                assertEquals(60.0, refreshed.currentMonth.amount.terms.single().value)
            }
        }

    @Test
    fun `a category never posted to shows no figure at all`() = runTest(dispatcher) {
        val repository = FakeCategoryRepository()
        val vm = viewModel(repository)

        vm.uiState.test {
            assertEquals(ViewCategoryUiState.Loading, awaitItem())
            repository.emit(category(dimensionId = 11))
            assertEquals(
                CategoryOverview.Empty,
                assertIs<ViewCategoryUiState.Content>(awaitItem()).overview,
                "a zero in the highlight reads as a failure; this is an absence",
            )
        }
    }

    @Test
    fun `entries dated ahead of the current month reach no figure`() = runTest(dispatcher) {
        val repository = FakeCategoryRepository()
        val entries = FakeEntryRepository(
            series = mapOf(
                10L to mapOf(
                    currentMonth.back(1) to 100.0,
                    currentMonth to 30.0,
                    // A purchase in instalments writes these today.
                    currentMonth.ahead(1) to 30.0,
                    currentMonth.ahead(2) to 30.0,
                ),
            ),
        )
        val vm = viewModel(categoryRepository = repository, entryRepository = entries)

        vm.uiState.test {
            assertEquals(ViewCategoryUiState.Loading, awaitItem())
            repository.emit(category())
            val overview = assertIs<CategoryOverview.Active>(
                assertIs<ViewCategoryUiState.Content>(awaitItem()).overview,
            )

            assertEquals(30.0, overview.currentMonth.amount.terms.single().value)
            assertEquals(100.0, checkNotNull(overview.window).total.terms.single().value)
        }
    }

    @Test
    fun loadingThenContent() = runTest(dispatcher) {
        val repository = FakeCategoryRepository()
        val vm = viewModel(repository)

        vm.uiState.test {
            assertEquals(ViewCategoryUiState.Loading, awaitItem())
            repository.emit(category(name = "Food"))
            assertEquals("Food", assertIs<ViewCategoryUiState.Content>(awaitItem()).category.name)
        }
    }

    @Test
    fun firstEmissionNullShowsErrorAndRecordsException() = runTest(dispatcher) {
        val repository = FakeCategoryRepository()
        val crashlytics = FakeCrashlytics()
        val vm = viewModel(repository, crashlytics)

        vm.uiState.test {
            assertEquals(ViewCategoryUiState.Loading, awaitItem())
            repository.emit(null)
            assertEquals(ViewCategoryUiState.Error, awaitItem())
        }

        assertEquals(1, crashlytics.recorded.size)
        assertTrue(crashlytics.recorded.first() is DetailNotFoundException)
    }

    @Test
    fun deletionAfterContentEmitsDismiss() = runTest(dispatcher) {
        val repository = FakeCategoryRepository()
        val vm = viewModel(repository)

        turbineScope {
            val state = vm.uiState.testIn(backgroundScope)
            val events = vm.events.testIn(backgroundScope)

            assertEquals(ViewCategoryUiState.Loading, state.awaitItem())
            repository.emit(category())
            assertIs<ViewCategoryUiState.Content>(state.awaitItem())

            repository.emit(null)
            assertIs<ViewCategoryEvent.Dismiss>(events.awaitItem())
            state.expectNoEvents()

            state.cancel()
            events.cancel()
        }
    }
}

private fun YearMonth.back(months: Int): YearMonth {
    var month = this
    repeat(months) { month = month.minusMonth() }
    return month
}

private fun YearMonth.ahead(months: Int): YearMonth {
    var month = this
    repeat(months) { month = month.plusMonth() }
    return month
}

internal class FakeAccountCurrencies(
    private val inUse: List<String> = listOf("BRL"),
) : GetAccountCurrenciesUseCase {
    override suspend fun invoke() = AccountCurrencies(inUse = inUse, ofDefaultAccount = inUse.firstOrNull())
}
