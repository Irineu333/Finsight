package com.neoutils.finsight.domain.repository

import com.neoutils.finsight.domain.model.Budget
import kotlinx.coroutines.flow.Flow

interface IBudgetRepository {
    fun observeAllBudgets(): Flow<List<Budget>>
    suspend fun getAllBudgets(): List<Budget>
    suspend fun insert(budget: Budget)
    suspend fun update(budget: Budget)
    suspend fun delete(budget: Budget)
    suspend fun hasBudgetForCategory(categoryId: Long): Boolean

    /**
     * Whether any budget still names this recurring as its base income — one of the
     * two guards that decide whether the recurring may be deleted or must be archived.
     */
    suspend fun hasBudgetForRecurring(recurringId: Long): Boolean
}
