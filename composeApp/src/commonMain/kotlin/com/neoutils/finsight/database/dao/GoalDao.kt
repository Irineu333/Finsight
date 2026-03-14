package com.neoutils.finsight.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.neoutils.finsight.database.entity.GoalCategoryEntity
import com.neoutils.finsight.database.entity.GoalEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GoalDao {
    @Query("SELECT * FROM goals ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<GoalEntity>>

    @Query("SELECT * FROM goal_categories")
    fun observeAllGoalCategories(): Flow<List<GoalCategoryEntity>>

    @Insert
    suspend fun insert(goal: GoalEntity): Long

    @Insert
    suspend fun insertGoalCategory(entity: GoalCategoryEntity)

    @Query("DELETE FROM goal_categories WHERE goalId = :goalId")
    suspend fun deleteGoalCategories(goalId: Long)

    @Update
    suspend fun update(goal: GoalEntity)

    @Delete
    suspend fun delete(goal: GoalEntity)
}
