package com.neoutils.finsight.database.repository

import com.neoutils.finsight.database.dao.GoalDao
import com.neoutils.finsight.database.entity.GoalCategoryEntity
import com.neoutils.finsight.database.mapper.GoalMapper
import com.neoutils.finsight.domain.model.Goal
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.repository.IGoalRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

class GoalRepository(
    private val dao: GoalDao,
    private val mapper: GoalMapper,
    private val categoryRepository: ICategoryRepository,
) : IGoalRepository {

    override fun observeAllGoals(): Flow<List<Goal>> {
        return combine(
            dao.observeAll(),
            dao.observeAllGoalCategories(),
            categoryRepository.observeAllCategories(),
        ) { entities, goalCategories, categories ->
            val categoryMap = categories.associateBy { it.id }
            val goalCategoryMap = goalCategories.groupBy { it.goalId }
            entities.map { entity ->
                val entityCategories = goalCategoryMap[entity.id]
                    ?.mapNotNull { categoryMap[it.categoryId] }
                    ?: emptyList()
                mapper.toDomain(entity, entityCategories)
            }
        }
    }

    override suspend fun getAllGoals(): List<Goal> {
        return observeAllGoals().first()
    }

    override suspend fun insert(goal: Goal) {
        val id = dao.insert(mapper.toEntity(goal))
        goal.categories.forEach { category ->
            dao.insertGoalCategory(GoalCategoryEntity(goalId = id, categoryId = category.id))
        }
    }

    override suspend fun update(goal: Goal) {
        dao.update(mapper.toEntity(goal))
        dao.deleteGoalCategories(goal.id)
        goal.categories.forEach { category ->
            dao.insertGoalCategory(GoalCategoryEntity(goalId = goal.id, categoryId = category.id))
        }
    }

    override suspend fun delete(goal: Goal) {
        dao.delete(mapper.toEntity(goal))
    }
}
