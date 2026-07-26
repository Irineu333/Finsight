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

    @Query("SELECT COUNT(*) FROM budget_categories WHERE categoryId = :categoryId")
    suspend fun countByCategory(categoryId: Long): Int

    /**
     * Whether any budget still names this recurring as its base income. `budgets`
     * declares no foreign key, so this is the only thing that stands between a
     * deleted recurring and a percentage limit silently reading as zero.
     */
    @Query("SELECT EXISTS(SELECT 1 FROM budgets WHERE recurringId = :recurringId)")
    suspend fun existsByRecurring(recurringId: Long): Boolean

    @Update
    suspend fun update(budget: BudgetEntity)

    @Delete
    suspend fun delete(budget: BudgetEntity)
}
