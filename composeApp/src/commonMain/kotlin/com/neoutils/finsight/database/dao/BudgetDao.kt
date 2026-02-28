package com.neoutils.finsight.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.neoutils.finsight.database.entity.BudgetCategoryEntity
import com.neoutils.finsight.database.entity.BudgetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budget_categories")
    fun observeAllBudgetCategories(): Flow<List<BudgetCategoryEntity>>

    @Insert
    suspend fun insert(budget: BudgetEntity): Long

    @Insert
    suspend fun insertBudgetCategory(entity: BudgetCategoryEntity)

    @Query("DELETE FROM budget_categories WHERE budgetId = :budgetId")
    suspend fun deleteBudgetCategories(budgetId: Long)

    @Update
    suspend fun update(budget: BudgetEntity)

    @Delete
    suspend fun delete(budget: BudgetEntity)
}
