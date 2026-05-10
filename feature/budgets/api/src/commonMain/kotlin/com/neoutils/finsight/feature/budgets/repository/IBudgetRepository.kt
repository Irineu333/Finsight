package com.neoutils.finsight.feature.budgets.repository

import com.neoutils.finsight.feature.budgets.model.Budget
import kotlinx.coroutines.flow.Flow

interface IBudgetRepository {
    fun observeAllBudgets(): Flow<List<Budget>>
    suspend fun getBudgetById(id: Long): Budget?
    suspend fun getAllBudgets(): List<Budget>
    suspend fun insert(budget: Budget)
    suspend fun update(budget: Budget)
    suspend fun delete(budget: Budget)
}
