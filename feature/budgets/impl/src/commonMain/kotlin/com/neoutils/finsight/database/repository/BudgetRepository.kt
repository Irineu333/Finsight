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
            categoryRepository.observeAllCategories(),
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
}
