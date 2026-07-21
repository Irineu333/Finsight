@file:OptIn(ExperimentalCoroutinesApi::class)

package com.neoutils.finsight.ui.modal.viewCategory

import app.cash.turbine.test
import app.cash.turbine.turbineScope
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.exception.DetailNotFoundException
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.model.Budget
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.repository.AccountFlows
import com.neoutils.finsight.domain.repository.IBudgetRepository
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.repository.IRecurringRepository
import com.neoutils.finsight.ui.model.RetireAction
import com.neoutils.finsight.extension.toYearMonth
import com.neoutils.finsight.ui.icons.CategoryLazyIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlin.time.Clock
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

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
        fun emit(category: Category?) { byId.tryEmit(category) }
        override fun observeCategoryById(id: Long): Flow<Category?> = byId
        override fun observeAllCategories(): Flow<List<Category>> = throw NotImplementedError()
        override suspend fun getAllCategories(): List<Category> = throw NotImplementedError()
        override suspend fun getAllCategoriesIncludingClosed(): List<Category> = getAllCategories()
        override fun observeAllCategoriesIncludingClosed(): Flow<List<Category>> = observeAllCategories()
        override fun observeCategoriesByType(type: Category.Type): Flow<List<Category>> = throw NotImplementedError()
        override suspend fun getCategoryById(id: Long): Category? = throw NotImplementedError()
        override suspend fun insert(category: Category) = throw NotImplementedError()
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
        override suspend fun balanceInMonth(month: YearMonth, accountId: Long): Double = balances[accountId] ?: 0.0
        override suspend fun balance(accountId: Long): Double = throw NotImplementedError()
        override suspend fun hasEntries(accountId: Long): Boolean = false
        override suspend fun entryCountInMonth(month: YearMonth, accountId: Long): Int = counts[accountId] ?: 0
        override suspend fun getEntriesByTransaction(transactionId: Long): List<Entry> = throw NotImplementedError()
        override fun observeEntriesByTransaction(transactionId: Long): Flow<List<Entry>> = throw NotImplementedError()
        override fun observeLedgerChanges(): Flow<Unit> = ledger
        override suspend fun accountFlows(month: YearMonth, accountId: Long): AccountFlows = throw NotImplementedError()
        override suspend fun balanceUpTo(target: YearMonth, accountId: Long?): Double = throw NotImplementedError()
        override suspend fun invoiceOwed(invoiceId: Long): Double = throw NotImplementedError()
        override suspend fun invoiceFlows(invoiceId: Long): com.neoutils.finsight.domain.repository.InvoiceFlows = throw NotImplementedError()
        override suspend fun cardMonthFlows(month: YearMonth): com.neoutils.finsight.domain.repository.CardMonthFlows = throw NotImplementedError()
        override suspend fun netWorth(): Double = throw NotImplementedError()
        override suspend fun categoryTotals(
            categoryType: AccountType,
            startDate: LocalDate,
            endDate: LocalDate,
            siblingAccountIds: List<Long>,
        ): Map<Long, Double> = throw NotImplementedError()
        override suspend fun categoryTotalsForInvoices(
            categoryType: AccountType,
            invoiceIds: List<Long>,
        ): Map<Long, Double> = throw NotImplementedError()
    }

    private fun category(
        id: Long = 1L,
        name: String = "Food",
        accountId: Long = 10L,
    ) = Category(
        id = id,
        name = name,
        icon = CategoryLazyIcon("shopping"),
        type = Category.Type.EXPENSE,
        createdAt = 0L,
        accountId = accountId,
    )

    private fun viewModel(
        categoryRepository: FakeCategoryRepository,
        crashlytics: FakeCrashlytics = FakeCrashlytics(),
        entryRepository: FakeEntryRepository = FakeEntryRepository(),
        recurringRepository: IRecurringRepository = FakeRecurringRepository(),
        budgetRepository: IBudgetRepository = FakeBudgetRepository(),
    ) = ViewCategoryViewModel(
        categoryId = 1L,
        categoryRepository = categoryRepository,
        entryRepository = entryRepository,
        recurringRepository = recurringRepository,
        budgetRepository = budgetRepository,
        crashlytics = crashlytics,
    )

    private class FakeRecurringRepository(private val has: Boolean = false) : IRecurringRepository {
        override suspend fun hasRecurringForCategory(categoryId: Long) = has
        override suspend fun hasRecurringForAccount(accountId: Long) = false
        override suspend fun hasRecurringForCreditCard(creditCardId: Long) = false
        override fun observeAllRecurring(): Flow<List<Recurring>> = flowOf(emptyList())
        override fun observeRecurringById(id: Long): Flow<Recurring?> = flowOf(null)
        override suspend fun insert(recurring: Recurring) = throw NotImplementedError()
        override suspend fun update(recurring: Recurring) = throw NotImplementedError()
        override suspend fun delete(recurring: Recurring) = throw NotImplementedError()
    }

    private class FakeBudgetRepository(private val has: Boolean = false) : IBudgetRepository {
        override suspend fun hasBudgetForCategory(categoryId: Long) = has
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
            assertEquals(42.5, content.totalAmount)
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
            assertEquals(42.5, assertIs<ViewCategoryUiState.Content>(awaitItem()).totalAmount)

            entries.balances = mapOf(10L to 60.0)
            entries.counts = mapOf(10L to 3)
            entries.ledger.emit(Unit)

            val refreshed = assertIs<ViewCategoryUiState.Content>(awaitItem())
            assertEquals(60.0, refreshed.totalAmount)
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
            assertEquals(0.0, content.totalAmount)
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
