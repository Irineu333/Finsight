@file:OptIn(ExperimentalCoroutinesApi::class)

package com.neoutils.finsight.ui.modal.viewCategory

import app.cash.turbine.test
import app.cash.turbine.turbineScope
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.exception.DetailNotFoundException
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.model.ExchangeRate
import com.neoutils.finsight.domain.model.Budget
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.repository.AccountFlows
import com.neoutils.finsight.domain.repository.IBaseCurrencyRepository
import com.neoutils.finsight.domain.repository.IBudgetRepository
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.repository.IExchangeRateRepository
import com.neoutils.finsight.domain.repository.IRecurringRepository
import com.neoutils.finsight.domain.usecase.AccountCurrencies
import com.neoutils.finsight.domain.usecase.ConsolidateMoneyUseCase
import com.neoutils.finsight.domain.usecase.GetAccountCurrenciesUseCase
import com.neoutils.finsight.domain.usecase.ObserveConsolidationChangesUseCase
import com.neoutils.finsight.domain.usecase.ResolveCategoryRetirabilityUseCase
import com.neoutils.finsight.domain.usecase.UnarchiveCategoryUseCase
import com.neoutils.finsight.ui.model.RetireAction
import com.neoutils.finsight.extension.toYearMonth
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
import kotlin.time.Clock
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import com.neoutils.finsight.domain.model.MoneyByCurrency
import com.neoutils.finsight.domain.repository.AssetMonthFlowsByCurrency
import com.neoutils.finsight.domain.repository.DimensionFlowsByCurrency
import com.neoutils.finsight.domain.repository.LiabilityMonthFlowsByCurrency
import com.neoutils.finsight.domain.repository.ScopeStatsByCurrency

class ViewCategoryViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

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
        override suspend fun getCategoryByDimensionId(dimensionId: Long): Category? = null
        override suspend fun archive(id: Long) = Unit
        override suspend fun unarchive(id: Long) { unarchived += id }
        override suspend fun existsByName(name: String, ignoreId: Long): Boolean = false

        override suspend fun insert(category: Category) = throw NotImplementedError()
        override suspend fun insertAll(categories: List<Category>) = throw NotImplementedError()
        override suspend fun update(category: Category) = throw NotImplementedError()
        override suspend fun delete(category: Category) = throw NotImplementedError()
    }

    // The ledger reader: Σ entries of the category account in the month, and the entry
    // count. The month-filtering and category-filtering correctness now lives in SQL
    // (EntryDao, covered by EntryRepository/DB tests); here we only pin the numbers the
    // ViewModel surfaces for the account it reads.
    private class FakeEntryRepository(
        var balances: Map<Long, Double> = emptyMap(),
        var counts: Map<Long, Int> = emptyMap(),
    ) : IEntryRepository {
        /** Stands in for Room's invalidation: emit after moving the ledger. */
        val ledger = MutableSharedFlow<Unit>(replay = 1).also { it.tryEmit(Unit) }
        override suspend fun dimensionBalanceInMonthByCurrency(month: YearMonth, dimensionId: Long) =
            balances[dimensionId]
                ?.let { com.neoutils.finsight.domain.model.MoneyByCurrency.of("BRL", it) }
                ?: com.neoutils.finsight.domain.model.MoneyByCurrency.zero

        override suspend fun balance(accountId: Long): Double = throw NotImplementedError()
        override suspend fun hasEntries(accountId: Long): Boolean = false
        override suspend fun hasEntriesForDimension(dimensionId: Long): Boolean = false
        override suspend fun dimensionEntryCountInMonth(month: YearMonth, dimensionId: Long): Int = counts[dimensionId] ?: 0
        override suspend fun getEntriesByTransaction(transactionId: Long): List<Entry> = throw NotImplementedError()
        override fun observeEntriesByTransaction(transactionId: Long): Flow<List<Entry>> = throw NotImplementedError()
        override fun observeLedgerChanges(): Flow<Unit> = ledger
        override suspend fun accountFlows(month: YearMonth, accountId: Long): AccountFlows = throw NotImplementedError()
    
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

    private fun category(
        id: Long = 1L,
        name: String = "Food",
        accountId: Long = 10L,
        isArchived: Boolean = false,
    ) = Category(
        id = id,
        name = name,
        icon = CategoryLazyIcon("shopping"),
        type = Category.Type.EXPENSE,
        createdAt = 0L,
        isArchived = isArchived,
        dimensionId = accountId,
    )

    private fun viewModel(
        categoryRepository: FakeCategoryRepository,
        crashlytics: FakeCrashlytics = FakeCrashlytics(),
        entryRepository: FakeEntryRepository = FakeEntryRepository(),
        recurringRepository: IRecurringRepository = FakeRecurringRepository(),
        budgetRepository: IBudgetRepository = FakeBudgetRepository(),
        unarchiveCategory: UnarchiveCategoryUseCase = UnarchiveCategoryUseCase(categoryRepository),
    ) = ViewCategoryViewModel(
        categoryId = 1L,
        categoryRepository = categoryRepository,
        entryRepository = entryRepository,
        resolveRetirability = ResolveCategoryRetirabilityUseCase(
            entryRepository = entryRepository,
            budgetRepository = budgetRepository,
            recurringRepository = recurringRepository,
        ),
        unarchiveCategory = unarchiveCategory,
        consolidateMoney = ConsolidateMoneyUseCase(
            baseCurrencyRepository = FakeBaseCurrencyRepository(),
            exchangeRateRepository = FakeExchangeRateRepository(),
            getAccountCurrencies = FakeAccountCurrencies(),
        ),
        observeConsolidationChanges = ObserveConsolidationChangesUseCase(
            entryRepository = entryRepository,
            baseCurrencyRepository = FakeBaseCurrencyRepository(),
            exchangeRateRepository = FakeExchangeRateRepository(),
        ),
        crashlytics = crashlytics,
    )

    private class FakeBaseCurrencyRepository(base: String = "BRL") : IBaseCurrencyRepository {
        private val flow = MutableStateFlow(base)
        override fun observe(): StateFlow<String> = flow
        override suspend fun set(currency: String) { flow.value = currency }
    }

    private class FakeExchangeRateRepository : IExchangeRateRepository {
        override suspend fun rateAsOf(currency: String, date: LocalDate): ExchangeRate? = null
        override suspend fun ratesAsOf(date: LocalDate): Map<String, ExchangeRate> = emptyMap()
        override fun observeAll(): Flow<List<ExchangeRate>> = flowOf(emptyList())
        override suspend fun save(rate: ExchangeRate) = Unit
        override suspend fun remove(rate: ExchangeRate) = Unit
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

    // The ViewModel starts on the current month (Clock.System.now()).
    private val currentMonth = Clock.System.now().toYearMonth()

    // Characterizes the current totalAmount (Σ amount of the category account) and
    // transactionCount (leg count) for a category in the selected month — now read from
    // the ledger (task 4.1). The numbers (42.5, 2) must survive the flip.
    @Test
    fun `content characterizes total amount and transaction count for the month`() = runTest(dispatcher) {
        val repository = FakeCategoryRepository()
        val vm = viewModel(
            categoryRepository = repository,
            // EXPENSE category account (id 10): debit-positive natural balance reads as
            // +42.5 spent, from two entries.
            entryRepository = FakeEntryRepository(
                balances = mapOf(10L to 42.5),
                counts = mapOf(10L to 2),
            ),
        )

        vm.uiState.test {
            assertEquals(ViewCategoryUiState.Loading, awaitItem())
            repository.emit(category(id = 1L, name = "Food", accountId = 10L))
            val content = assertIs<ViewCategoryUiState.Content>(awaitItem())
            assertEquals(42.5, content.totalAmount.terms.single().value)
            assertEquals(2, content.transactionCount)
        }
    }

    @Test
    fun `an unused category with no dependents offers delete`() = runTest(dispatcher) {
        val repository = FakeCategoryRepository()
        val vm = viewModel(categoryRepository = repository)
        vm.uiState.test {
            assertEquals(ViewCategoryUiState.Loading, awaitItem())
            repository.emit(category(id = 1L, name = "Food", accountId = 10L))
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
            repository.emit(category(id = 1L, name = "Food", accountId = 10L))
            assertEquals(RetireAction.ARCHIVE, assertIs<ViewCategoryUiState.Content>(awaitItem()).retireAction)
        }
    }

    @Test
    fun `a category a recurring points at offers archive instead of delete`() = runTest(dispatcher) {
        val repository = FakeCategoryRepository()
        val vm = viewModel(categoryRepository = repository, recurringRepository = FakeRecurringRepository(has = true))
        vm.uiState.test {
            assertEquals(ViewCategoryUiState.Loading, awaitItem())
            repository.emit(category(id = 1L, name = "Food", accountId = 10L))
            assertEquals(RetireAction.ARCHIVE, assertIs<ViewCategoryUiState.Content>(awaitItem()).retireAction)
        }
    }

    @Test
    fun `an archived category is shown archived, so the view offers unarchive`() = runTest(dispatcher) {
        val repository = FakeCategoryRepository()
        val vm = viewModel(categoryRepository = repository)
        vm.uiState.test {
            assertEquals(ViewCategoryUiState.Loading, awaitItem())
            repository.emit(category(id = 1L, isArchived = true))
            assertTrue(assertIs<ViewCategoryUiState.Content>(awaitItem()).category.isArchived)
        }
    }

    @Test
    fun `a non-archived category is shown active, so the view offers retire`() = runTest(dispatcher) {
        val repository = FakeCategoryRepository()
        val vm = viewModel(categoryRepository = repository)
        vm.uiState.test {
            assertEquals(ViewCategoryUiState.Loading, awaitItem())
            repository.emit(category(id = 1L, isArchived = false))
            assertFalse(assertIs<ViewCategoryUiState.Content>(awaitItem()).category.isArchived)
        }
    }

    @Test
    fun `the unarchive action unarchives the shown category`() = runTest(dispatcher) {
        val repository = FakeCategoryRepository()
        val vm = viewModel(categoryRepository = repository)
        vm.uiState.test {
            assertEquals(ViewCategoryUiState.Loading, awaitItem())
            repository.emit(category(id = 5L, isArchived = true))
            assertIs<ViewCategoryUiState.Content>(awaitItem())

            vm.onAction(ViewCategoryAction.Unarchive)
            runCurrent()

            assertEquals(listOf(5L), repository.unarchived)
        }
    }

    @Test
    fun `the total refreshes when the ledger moves without the category changing`() = runTest(dispatcher) {
        // The figures are SQL aggregates, so nothing about the category row changes
        // when a transaction is written. Without a ledger signal the screen kept
        // showing the old total while the ledger had already moved.
        val repository = FakeCategoryRepository()
        val entries = FakeEntryRepository(balances = mapOf(10L to 42.5), counts = mapOf(10L to 2))
        val vm = viewModel(categoryRepository = repository, entryRepository = entries)

        vm.uiState.test {
            assertEquals(ViewCategoryUiState.Loading, awaitItem())
            repository.emit(category(id = 1L, name = "Food", accountId = 10L))
            assertEquals(42.5, assertIs<ViewCategoryUiState.Content>(awaitItem()).totalAmount.terms.single().value)

            entries.balances = mapOf(10L to 60.0)
            entries.counts = mapOf(10L to 3)
            entries.ledger.emit(Unit)

            val refreshed = assertIs<ViewCategoryUiState.Content>(awaitItem())
            assertEquals(60.0, refreshed.totalAmount.terms.single().value)
            assertEquals(3, refreshed.transactionCount)
        }
    }

    @Test
    fun `category never posted to reads zero`() = runTest(dispatcher) {
        val repository = FakeCategoryRepository()
        val vm = viewModel(repository)

        vm.uiState.test {
            assertEquals(ViewCategoryUiState.Loading, awaitItem())
            repository.emit(category(id = 1L, accountId = 11))
            val content = assertIs<ViewCategoryUiState.Content>(awaitItem())
            assertEquals(0.0, content.totalAmount.terms.single().value)
            assertEquals(0, content.transactionCount)
        }
    }

    @Test
    fun loadingThenContent() = runTest(dispatcher) {
        val repository = FakeCategoryRepository()
        val vm = viewModel(repository)

        vm.uiState.test {
            assertEquals(ViewCategoryUiState.Loading, awaitItem())
            repository.emit(category(id = 1L, name = "Food"))
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
            repository.emit(category(id = 1L))
            assertIs<ViewCategoryUiState.Content>(state.awaitItem())

            repository.emit(null)
            assertIs<ViewCategoryEvent.Dismiss>(events.awaitItem())
            state.expectNoEvents()

            state.cancel()
            events.cancel()
        }
    }
}

internal class FakeAccountCurrencies(
    private val inUse: List<String> = listOf("BRL"),
) : GetAccountCurrenciesUseCase {
    override suspend fun invoke() = AccountCurrencies(inUse = inUse, ofDefaultAccount = inUse.firstOrNull())
}
