package com.neoutils.finsight.domain.repository

import com.neoutils.finsight.domain.model.Goal
import kotlinx.coroutines.flow.Flow

interface IGoalRepository {
    fun observeAllGoals(): Flow<List<Goal>>
    suspend fun getAllGoals(): List<Goal>
    suspend fun insert(goal: Goal)
    suspend fun update(goal: Goal)
    suspend fun delete(goal: Goal)
}
