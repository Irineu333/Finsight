package com.neoutils.finsight.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.neoutils.finsight.database.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

// A category is closed when *its* ledger account is (design D21) — it keeps no
// copy of the flag. Every category has an account, created with it, so this is a
// plain join.
private const val OPEN_CATEGORIES =
    "SELECT c.* FROM categories c JOIN accounts a ON a.id = c.accountId " +
        "WHERE a.isClosed = 0"

// Rendering history needs the closed ones too: a transaction on a category that
// was later closed must still show its name.
private const val ALL_CATEGORIES =
    "SELECT c.*, a.isClosed AS isClosed FROM categories c JOIN accounts a ON a.id = c.accountId"

@Dao
interface CategoryDao {
    @Query(ALL_CATEGORIES + " ORDER BY c.createdAt ASC")
    suspend fun getAllCategoriesIncludingClosed(): List<CategoryWithClosure>

    @Query(ALL_CATEGORIES + " ORDER BY c.createdAt ASC")
    fun observeAllCategoriesIncludingClosed(): Flow<List<CategoryWithClosure>>

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
