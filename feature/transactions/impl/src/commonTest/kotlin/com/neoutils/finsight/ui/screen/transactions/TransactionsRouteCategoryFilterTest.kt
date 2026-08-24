@file:OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)

package com.neoutils.finsight.ui.screen.transactions

import app.cash.turbine.test
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.model.SpendingSubject
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.extension.toYearMonth
import com.neoutils.finsight.ui.icons.CategoryLazyIcon
import com.neoutils.finsight.ui.screen.transactions.TransactionsUiState.ListState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * The value of the analytic axis arriving **by navigation**, from the detail of a category.
 *
 * What matters here is that it is not a second cutting mechanism beside the control: the
 * list comes up narrowed, the control says by what, and the neutral state undoes it exactly
 * as it undoes a choice made on this screen. An archived category resolves like any other —
 * the list is fed by `observeAllCategoriesIncludingClosed`, because its history is the
 * reason the cut has to stay reachable at all.
 */
class TransactionsRouteCategoryFilterTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setup() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private val month = Clock.System.now().toYearMonth()

    private val account = Account(id = 1, name = "A", type = AccountType.ASSET, currency = "BRL")
    private val expenseAcc = Account(id = 101, name = "expense", type = AccountType.EXPENSE, currency = "BRL")

    private val bakery = Category(
        id = 7, name = "Bakery", icon = CategoryLazyIcon("food"),
        type = Category.Type.EXPENSE, createdAt = 0L, dimensionId = 70,
    )
    private val retired = Category(
        id = 8, name = "Retired", icon = CategoryLazyIcon("food"),
        type = Category.Type.EXPENSE, createdAt = 0L, dimensionId = 80, isArchived = true,
    )

    private fun date(day: Int) = LocalDate(month.year, month.month, day)

    private fun entry(acc: Account, amount: Double, dimensionId: Long? = null) =
        Entry(account = acc, amount = (amount * 100).toLong(), dimensionId = dimensionId)

    private fun op(id: Long, day: Int, dimensionId: Long?) = Transaction(
        id = id, title = null, date = date(day),
        entries = listOf(entry(account, -30.0), entry(expenseAcc, 30.0, dimensionId)),
    )

    private val croissant = op(10, 4, bakery.dimensionId)
    private val history = op(11, 5, retired.dimensionId)
    private val newspaper = op(12, 6, dimensionId = null)

    private val everything = listOf(croissant, history, newspaper)

    private fun viewModel(filterCategoryId: Long?) = TransactionsViewModel(
        filterLabel = null, filterTarget = null, filterCategoryId = filterCategoryId,
        transactionRepository = FakeTransactionRepository(everything),
        categoryRepository = CategoriesInStore(listOf(bakery, retired)),
        installmentRepository = NoInstallments,
        entryRepository = FakeLedger(everything),
        consolidateMoney = consolidator(),
        observeConsolidationChanges = FakeLedger(everything).consolidationChanges(),
        baseCurrencyRepository = FakeBaseCurrency(),
        clock = Clock.System,
    )

    private val TransactionsUiState.listed
        get() = (listState as? ListState.Content)
            ?.transactions
            ?.values
            ?.flatten()
            ?.map { it.id }
            ?.toSet()
            .orEmpty()

    private suspend fun stateOf(
        filterCategoryId: Long?,
        actions: List<TransactionsAction> = emptyList(),
        settled: (TransactionsUiState) -> Boolean = { it.listState !is ListState.Loading },
    ): TransactionsUiState {
        val vm = viewModel(filterCategoryId)
        var result = TransactionsUiState()
        vm.uiState.test {
            var state = awaitItem()
            while (state.listState is ListState.Loading) state = awaitItem()

            actions.forEach { vm.onAction(it) }
            while (!settled(state)) state = awaitItem()

            result = state
            cancelAndIgnoreRemainingEvents()
        }
        return result
    }

    @Test
    fun `the list opens cut by the category, and the control says so`() = runTest(dispatcher) {
        val state = stateOf(filterCategoryId = bakery.id)

        assertEquals(setOf(croissant.id), state.listed)
        assertEquals(SpendingSubject.Categorized(bakery), state.selectedSubject)
    }

    @Test
    fun `the neutral state undoes the cut that arrived by navigation`() = runTest(dispatcher) {
        val state = stateOf(
            filterCategoryId = bakery.id,
            actions = listOf(TransactionsAction.SelectSubject(null)),
            settled = { it.selectedSubject == null },
        )

        assertEquals(everything.map { it.id }.toSet(), state.listed)
        assertEquals(null, state.selectedSubject)
    }

    @Test
    fun `an archived category is accepted as the initial value`() = runTest(dispatcher) {
        val state = stateOf(filterCategoryId = retired.id)

        assertEquals(SpendingSubject.Categorized(retired), state.selectedSubject)
        assertEquals(setOf(history.id), state.listed)
    }

    @Test
    fun `an id matching no category opens the list neutral, not empty`() = runTest(dispatcher) {
        val state = stateOf(filterCategoryId = 4_242L)

        assertEquals(null, state.selectedSubject)
        assertEquals(everything.map { it.id }.toSet(), state.listed)
    }

    @Test
    fun `clearing the filters drops it too`() = runTest(dispatcher) {
        val state = stateOf(
            filterCategoryId = bakery.id,
            actions = listOf(TransactionsAction.ClearFilters),
            settled = { it.selectedSubject == null },
        )

        assertEquals(everything.map { it.id }.toSet(), state.listed)
    }
}

/** The category store this screen reads: archived rows included, since it shows history. */
private class CategoriesInStore(private val categories: List<Category>) : ICategoryRepository {
    override fun observeAllCategoriesIncludingClosed(): Flow<List<Category>> =
        MutableStateFlow(categories)

    override fun observeAllCategories(): Flow<List<Category>> =
        MutableStateFlow(categories.filterNot { it.isArchived })

    override fun observeCategoryById(id: Long): Flow<Category?> = throw NotImplementedError()
    override suspend fun getAllCategories(): List<Category> = categories.filterNot { it.isArchived }
    override suspend fun getAllCategoriesIncludingClosed(): List<Category> = categories
    override fun observeCategoriesByType(type: Category.Type): Flow<List<Category>> = throw NotImplementedError()
    override suspend fun getCategoryById(id: Long): Category? = categories.find { it.id == id }
    override suspend fun getCategoryBySystemKey(systemKey: String): Category? = null
    override suspend fun getCategoryByDimensionId(dimensionId: Long): Category? =
        categories.find { it.dimensionId == dimensionId }
    override suspend fun archive(id: Long) = Unit
    override suspend fun unarchive(id: Long) = Unit
    override suspend fun existsByName(name: String, ignoreId: Long): Boolean = false
    override suspend fun insert(category: Category) = throw NotImplementedError()
    override suspend fun insertAll(categories: List<Category>) = throw NotImplementedError()
    override suspend fun update(category: Category) = throw NotImplementedError()
    override suspend fun delete(category: Category) = throw NotImplementedError()
}
