package com.neoutils.finsight.database.repository

import com.neoutils.finsight.database.dao.BudgetDao
import com.neoutils.finsight.database.entity.BudgetCategoryEntity
import com.neoutils.finsight.database.mapper.BudgetMapper
import com.neoutils.finsight.domain.model.Budget
import com.neoutils.finsight.domain.repository.IBudgetRepository
import com.neoutils.finsight.domain.repository.ICategoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

class BudgetRepository(
    private val dao: BudgetDao,
    private val mapper: BudgetMapper,
    private val categoryRepository: ICategoryRepository,
) : IBudgetRepository {

    override fun observeAllBudgets(): Flow<List<Budget>> {
        return combine(
            dao.observeAll(),
            dao.observeAllBudgetCategories(),
            // Resolving a stored reference, not offering a choice — the closed ones
            // too. With the open-only list, archiving a budgeted category dropped it
            // here (FK intact), which fed three failures downstream: it vanished from
            // the budget, its spending fell out of the progress figure, and the next
            // edit — reseeded from this list — deleted the FK for good. Same shape as
            // the recurring hydration (§10b.1). The *form's* selector stays open-only,
            // so it is not offered for a new budget.
            categoryRepository.observeAllCategoriesIncludingClosed(),
        ) { entities, budgetCategories, categories ->
            val categoryMap = categories.associateBy { it.id }
            val budgetCategoryMap = budgetCategories.groupBy { it.budgetId }
            entities.map { entity ->
                val entityCategories = budgetCategoryMap[entity.id]
                    ?.mapNotNull { categoryMap[it.categoryId] }
                    ?: emptyList()
                mapper.toDomain(entity, entityCategories)
            }
        }
    }

    override suspend fun getAllBudgets(): List<Budget> {
        return observeAllBudgets().first()
    }

    override suspend fun insert(budget: Budget) {
        val id = dao.insert(mapper.toEntity(budget))
        budget.categories.forEach { category ->
            dao.insertBudgetCategory(BudgetCategoryEntity(budgetId = id, categoryId = category.id))
        }
    }

    override suspend fun update(budget: Budget) {
        dao.update(mapper.toEntity(budget))
        dao.deleteBudgetCategories(budget.id)
        budget.categories.forEach { category ->
            dao.insertBudgetCategory(BudgetCategoryEntity(budgetId = budget.id, categoryId = category.id))
        }
    }

    override suspend fun delete(budget: Budget) {
        dao.delete(mapper.toEntity(budget))
    }

    // `budget_categories.categoryId` is CASCADE: deleting the category would strip
    // it from every budget silently. The delete use case refuses instead.
    override suspend fun hasBudgetForCategory(categoryId: Long): Boolean =
        dao.countByCategory(categoryId) > 0
}
