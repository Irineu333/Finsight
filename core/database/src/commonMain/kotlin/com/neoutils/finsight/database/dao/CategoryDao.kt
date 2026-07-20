package com.neoutils.finsight.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.neoutils.finsight.database.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

// A category is closed when *its* ledger account is (design D21) — it keeps no
// copy of the flag. LEFT JOIN, because a category that never moved money has no
// account yet, and "never used" is not "closed".
private const val OPEN_CATEGORIES =
    "SELECT c.* FROM categories c LEFT JOIN accounts a ON a.id = c.accountId " +
        "WHERE COALESCE(a.isClosed, 0) = 0"

@Dao
interface CategoryDao {
    @Query(OPEN_CATEGORIES + " ORDER BY c.createdAt ASC")
    fun observeAllCategories(): Flow<List<CategoryEntity>>

    @Query(OPEN_CATEGORIES + " ORDER BY c.createdAt ASC")
    suspend fun getAllCategories(): List<CategoryEntity>

    @Query(OPEN_CATEGORIES + " AND c.type = :type ORDER BY c.createdAt ASC")
    fun observeCategoriesByType(type: CategoryEntity.Type): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getCategoryById(id: Long): CategoryEntity?

    @Query("SELECT * FROM categories WHERE id = :id")
    fun observeCategoryById(id: Long): Flow<CategoryEntity?>

    @Insert
    suspend fun insert(category: CategoryEntity)

    @Update
    suspend fun update(category: CategoryEntity)

    @Delete
    suspend fun delete(category: CategoryEntity)
}
