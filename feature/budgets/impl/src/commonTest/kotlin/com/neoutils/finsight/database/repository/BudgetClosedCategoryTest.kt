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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A closed category is filtered out by READ, never by write: `CategoryDao` excludes it,
 * so it simply stops appearing in `Budget.categories`. Nothing is destroyed and reopening
 * the category restores it on its own. What must hold is that the *budget* survives —
 * including the one whose only category was closed, which stays visible at zero progress.
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

    private class FakeBudgetDao(
        private val budgets: List<BudgetEntity>,
        private val links: List<BudgetCategoryEntity>,
    ) : BudgetDao {
        override fun observeAll(): Flow<List<BudgetEntity>> = flowOf(budgets)
        override fun observeAllBudgetCategories(): Flow<List<BudgetCategoryEntity>> = flowOf(links)
        override suspend fun insert(budget: BudgetEntity): Long = throw NotImplementedError()
        override suspend fun insertBudgetCategory(entity: BudgetCategoryEntity) = throw NotImplementedError()
        override suspend fun deleteBudgetCategories(budgetId: Long) = throw NotImplementedError()
        override suspend fun update(budget: BudgetEntity) = throw NotImplementedError()
        override suspend fun delete(budget: BudgetEntity) = throw NotImplementedError()
    }

    /** Mirrors `CategoryDao`, which already excludes categories whose ledger account is closed. */
    private class FakeCategoryRepository(private val live: List<Category>) : ICategoryRepository {
        override fun observeAllCategories(): Flow<List<Category>> = flowOf(live)
        override suspend fun getAllCategories(): List<Category> = live
        override suspend fun getAllCategoriesIncludingClosed(): List<Category> = getAllCategories()
        override fun observeAllCategoriesIncludingClosed(): Flow<List<Category>> = observeAllCategories()
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
        liveCategories: List<Category>,
    ) = BudgetRepository(
        dao = FakeBudgetDao(budgets, links),
        mapper = mapper,
        categoryRepository = FakeCategoryRepository(liveCategories),
    )

    @Test
    fun `a multi-category budget keeps only its live categories and counts only their spending`() = runTest {
        val repository = repository(
            budgets = listOf(budgetEntity(id = 1)),
            links = listOf(
                BudgetCategoryEntity(budgetId = 1, categoryId = food.id),
                BudgetCategoryEntity(budgetId = 1, categoryId = transport.id),
            ),
            liveCategories = listOf(food),
        )

        val budget = repository.observeAllBudgets().first().single()

        assertEquals(listOf(food), budget.categories)

        // Transport's account (11) still carries entries; a closed category must not spend.
        val progress = useCase(
            budgets = listOf(budget),
            categoryBalances = mapOf(10L to 30.0, 11L to 70.0),
        ).single()

        assertEquals(30.0, progress.spent)
    }

    @Test
    fun `a budget whose only category was closed stays visible with zero progress`() = runTest {
        val repository = repository(
            budgets = listOf(budgetEntity(id = 1)),
            links = listOf(BudgetCategoryEntity(budgetId = 1, categoryId = food.id)),
            liveCategories = emptyList(),
        )

        val budget = repository.observeAllBudgets().first().single()

        assertEquals(emptyList(), budget.categories)

        val progress = useCase(
            budgets = listOf(budget),
            categoryBalances = mapOf(10L to 30.0),
        ).single()

        assertEquals(0.0, progress.spent)
        assertEquals(0f, progress.progress)
        assertEquals(200.0, progress.remaining)
    }

    @Test
    fun `a budget with no categories round-trips without loss`() = runTest {
        val repository = repository(
            budgets = listOf(budgetEntity(id = 1)),
            links = emptyList(),
            liveCategories = emptyList(),
        )

        val budget = repository.observeAllBudgets().first().single()

        // `toEntity` no longer writes a categoryId: that column was dropped because its
        // CASCADE destroyed the whole budget when the listed category was deleted.
        assertEquals(budgetEntity(id = 1), mapper.toEntity(budget))
    }
}
