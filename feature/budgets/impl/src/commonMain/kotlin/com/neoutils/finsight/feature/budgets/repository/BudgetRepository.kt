package com.neoutils.finsight.feature.budgets.repository

import com.neoutils.finsight.core.database.dao.BudgetDao
import com.neoutils.finsight.core.database.entity.BudgetCategoryEntity
import com.neoutils.finsight.feature.budgets.mapper.BudgetMapper
import com.neoutils.finsight.feature.budgets.model.Budget
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

class BudgetRepository(
    private val dao: BudgetDao,
    private val mapper: BudgetMapper,
) : IBudgetRepository {

    override fun observeAllBudgets(): Flow<List<Budget>> {
        return combine(
            dao.observeAll(),
            dao.observeAllBudgetCategories(),
        ) { entities, budgetCategories ->
            val budgetCategoryMap = budgetCategories.groupBy { it.budgetId }
            entities.map { entity ->
                val ids = budgetCategoryMap[entity.id]?.map { it.categoryId } ?: emptyList()
                mapper.toDomain(entity, ids)
            }
        }
    }

    override suspend fun getBudgetById(id: Long): Budget? {
        val entity = dao.getById(id) ?: return null
        val categoryIds = dao.getBudgetCategoriesByBudgetId(id).map { it.categoryId }
        return mapper.toDomain(entity, categoryIds)
    }

    override suspend fun getAllBudgets(): List<Budget> {
        return observeAllBudgets().first()
    }

    override suspend fun insert(budget: Budget) {
        val id = dao.insert(mapper.toEntity(budget))
        budget.categoryIds.forEach { categoryId ->
            dao.insertBudgetCategory(BudgetCategoryEntity(budgetId = id, categoryId = categoryId))
        }
    }

    override suspend fun update(budget: Budget) {
        dao.update(mapper.toEntity(budget))
        dao.deleteBudgetCategories(budget.id)
        budget.categoryIds.forEach { categoryId ->
            dao.insertBudgetCategory(BudgetCategoryEntity(budgetId = budget.id, categoryId = categoryId))
        }
    }

    override suspend fun delete(budget: Budget) {
        dao.delete(mapper.toEntity(budget))
    }
}
