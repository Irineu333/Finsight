package com.neoutils.finsight.database.repository

import com.neoutils.finsight.database.dao.BudgetDao
import com.neoutils.finsight.database.entity.BudgetCategoryEntity
import com.neoutils.finsight.database.entity.BudgetEntity
import com.neoutils.finsight.database.mapper.BudgetMapper
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.usecase.CalculateBudgetProgressUseCase
import com.neoutils.finsight.ui.icons.CategoryLazyIcon
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A budgeted category that is later archived is **kept**, not filtered out — the same
 * decision as the recurring hydration (§10b.1). The budget resolves a stored reference,
 * so it reads from the include-closed list: the archived category stays in
 * `Budget.categories`, its name still renders, and its spending still counts. The
 * *form's* selector reads the open-only list, so an archived category is simply not
 * offered for a new budget.
 *
 * The open-only hydration this replaced fed three silent failures: the category vanished
 * from the budget, its spending fell out of the progress figure, and the next edit —
 * reseeded from the hydrated list — deleted its `budget_categories` row for good.
 */
class BudgetClosedCategoryTest {

    private val mapper = BudgetMapper()
    private val useCase = CalculateBudgetProgressUseCase()

    private fun category(id: Long, accountId: Long) = Category(
        id = id, name = "Cat$id", icon = CategoryLazyIcon("shopping"),
        type = Category.Type.EXPENSE, createdAt = 0L, accountId = accountId,
    )

    private val food = category(id = 1, accountId = 10)
    private val transport = category(id = 2, accountId = 11)

    private fun budgetEntity(id: Long) = BudgetEntity(
        id = id, iconCategoryId = 0, iconKey = "shopping", title = "Budget $id",
        amount = 200.0, period = "MONTHLY", createdAt = 0L,
    )

    /** Mutable, so an edit round-trip (delete + reinsert links) can be observed. */
    private class FakeBudgetDao(
        budgets: List<BudgetEntity>,
        links: List<BudgetCategoryEntity>,
    ) : BudgetDao {
        private val budgetsFlow = MutableStateFlow(budgets)
        private val linksFlow = MutableStateFlow(links)

        override fun observeAll(): Flow<List<BudgetEntity>> = budgetsFlow
        override fun observeAllBudgetCategories(): Flow<List<BudgetCategoryEntity>> = linksFlow
        override suspend fun insert(budget: BudgetEntity): Long = budget.id
        override suspend fun insertBudgetCategory(entity: BudgetCategoryEntity) {
            linksFlow.value = linksFlow.value + entity
        }
        override suspend fun deleteBudgetCategories(budgetId: Long) {
            linksFlow.value = linksFlow.value.filterNot { it.budgetId == budgetId }
        }
        override suspend fun update(budget: BudgetEntity) {
            budgetsFlow.value = budgetsFlow.value.map { if (it.id == budget.id) budget else it }
        }
        override suspend fun delete(budget: BudgetEntity) = throw NotImplementedError()
    }

    /**
     * Mirrors `CategoryDao`: the open-only stream excludes archived categories, the
     * include-closed stream keeps them. The gap between the two is exactly the bug.
     */
    private class FakeCategoryRepository(
        private val open: List<Category>,
        private val all: List<Category>,
    ) : ICategoryRepository {
        override fun observeAllCategories(): Flow<List<Category>> = MutableStateFlow(open)
        override suspend fun getAllCategories(): List<Category> = open
        override suspend fun getAllCategoriesIncludingClosed(): List<Category> = all
        override fun observeAllCategoriesIncludingClosed(): Flow<List<Category>> = MutableStateFlow(all)
        override fun observeCategoriesByType(type: Category.Type): Flow<List<Category>> = throw NotImplementedError()
        override suspend fun getCategoryById(id: Long): Category? = throw NotImplementedError()
        override fun observeCategoryById(id: Long): Flow<Category?> = throw NotImplementedError()
        override suspend fun insert(category: Category) = throw NotImplementedError()
        override suspend fun update(category: Category) = throw NotImplementedError()
        override suspend fun delete(category: Category) = throw NotImplementedError()
    }

    private fun repository(
        budgets: List<BudgetEntity>,
        links: List<BudgetCategoryEntity>,
        open: List<Category>,
        all: List<Category>,
    ) = BudgetRepository(
        dao = FakeBudgetDao(budgets, links),
        mapper = mapper,
        categoryRepository = FakeCategoryRepository(open, all),
    )

    @Test
    fun `a multi-category budget keeps its archived category and counts its spending`() = runTest {
        val repository = repository(
            budgets = listOf(budgetEntity(id = 1)),
            links = listOf(
                BudgetCategoryEntity(budgetId = 1, categoryId = food.id),
                BudgetCategoryEntity(budgetId = 1, categoryId = transport.id),
            ),
            open = listOf(food),               // transport archived
            all = listOf(food, transport),
        )

        val budget = repository.observeAllBudgets().first().single()

        assertEquals(listOf(food, transport), budget.categories)

        // Both accounts carry entries; the archived category spends like any other.
        val progress = useCase(
            budgets = listOf(budget),
            categoryBalances = mapOf(10L to 30.0, 11L to 70.0),
        ).single()

        assertEquals(100.0, progress.spent)
    }

    @Test
    fun `a budget whose only category was archived stays visible and still counts it`() = runTest {
        val repository = repository(
            budgets = listOf(budgetEntity(id = 1)),
            links = listOf(BudgetCategoryEntity(budgetId = 1, categoryId = food.id)),
            open = emptyList(),
            all = listOf(food),
        )

        val budget = repository.observeAllBudgets().first().single()

        assertEquals(listOf(food), budget.categories)

        val progress = useCase(
            budgets = listOf(budget),
            categoryBalances = mapOf(10L to 30.0),
        ).single()

        assertEquals(30.0, progress.spent)
    }

    @Test
    fun `editing a budget preserves an archived category's link`() = runTest {
        // The data-loss path the read tests never touched: `update` deletes every link
        // and reinserts `budget.categories`. Only because hydration now keeps the
        // archived category does it survive the round-trip. With the open-only list its
        // link would be gone and reopening the category would not bring it back.
        val repository = repository(
            budgets = listOf(budgetEntity(id = 1)),
            links = listOf(
                BudgetCategoryEntity(budgetId = 1, categoryId = food.id),
                BudgetCategoryEntity(budgetId = 1, categoryId = transport.id),
            ),
            open = listOf(food),               // transport archived
            all = listOf(food, transport),
        )

        val loaded = repository.observeAllBudgets().first().single()
        // A plain title edit that does not touch the category selection.
        repository.update(loaded.copy(title = "Renamed"))

        val reloaded = repository.observeAllBudgets().first().single()
        assertEquals("Renamed", reloaded.title)
        assertEquals(listOf(food, transport), reloaded.categories)
    }

    @Test
    fun `a budget with no categories round-trips without loss`() = runTest {
        val repository = repository(
            budgets = listOf(budgetEntity(id = 1)),
            links = emptyList(),
            open = emptyList(),
            all = emptyList(),
        )

        val budget = repository.observeAllBudgets().first().single()

        // `toEntity` no longer writes a categoryId: that column was dropped because its
        // CASCADE destroyed the whole budget when the listed category was deleted.
        assertEquals(budgetEntity(id = 1), mapper.toEntity(budget))
    }
}
