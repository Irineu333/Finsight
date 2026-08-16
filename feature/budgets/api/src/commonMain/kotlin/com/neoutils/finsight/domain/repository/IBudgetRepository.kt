package com.neoutils.finsight.domain.repository

import com.neoutils.finsight.domain.model.Budget
import kotlinx.coroutines.flow.Flow

interface IBudgetRepository {
    fun observeAllBudgets(): Flow<List<Budget>>
    suspend fun getAllBudgets(): List<Budget>

    /**
     * One budget by identity — the read the use cases resolve with, since a budget is
     * always operated on by the id its caller holds. Not another listing, so not
     * another implementation: it picks out of [getAllBudgets], which is the single
     * place a budget is hydrated with its categories.
     */
    suspend fun getBudgetById(id: Long): Budget? =
        getAllBudgets().firstOrNull { it.id == id }

    /**
     * Stores the budget and answers the identity it was given. The id is what a caller
     * needs in order to name what it just created — reporting it, or reaching it again.
     */
    suspend fun insert(budget: Budget): Long
    suspend fun update(budget: Budget)
    suspend fun delete(budget: Budget)
    suspend fun hasBudgetForCategory(categoryId: Long): Boolean

    /**
     * Whether any budget still names this recurring as its base income — one of the
     * two guards that decide whether the recurring may be deleted or must be archived.
     */
    suspend fun hasBudgetForRecurring(recurringId: Long): Boolean
}
