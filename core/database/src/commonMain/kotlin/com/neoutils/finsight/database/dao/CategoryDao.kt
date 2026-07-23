package com.neoutils.finsight.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.neoutils.finsight.database.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

// Closure is the category's own column now: it has no account to read it from
// (design D4), so the join these queries used to carry is gone with it.
private const val OPEN_CATEGORIES = "SELECT * FROM categories WHERE isArchived = 0"

// Rendering history needs the closed ones too: a transaction on a category that
// was later closed must still show its name.
private const val ALL_CATEGORIES = "SELECT * FROM categories"

@Dao
interface CategoryDao {
    @Query(ALL_CATEGORIES + " ORDER BY createdAt ASC")
    suspend fun getAllCategoriesIncludingClosed(): List<CategoryEntity>

    @Query(ALL_CATEGORIES + " ORDER BY createdAt ASC")
    fun observeAllCategoriesIncludingClosed(): Flow<List<CategoryEntity>>

    @Query(OPEN_CATEGORIES + " ORDER BY createdAt ASC")
    fun observeAllCategories(): Flow<List<CategoryEntity>>

    @Query(OPEN_CATEGORIES + " ORDER BY createdAt ASC")
    suspend fun getAllCategories(): List<CategoryEntity>

    @Query(OPEN_CATEGORIES + " AND type = :type ORDER BY createdAt ASC")
    fun observeCategoriesByType(type: CategoryEntity.Type): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getCategoryById(id: Long): CategoryEntity?

    @Query("SELECT * FROM categories WHERE id = :id")
    fun observeCategoryById(id: Long): Flow<CategoryEntity?>

    /** Resolves the facade a dimension belongs to — the inverse of [CategoryEntity.dimensionId]. */
    @Query("SELECT * FROM categories WHERE dimensionId = :dimensionId LIMIT 1")
    suspend fun getCategoryByDimensionId(dimensionId: Long): CategoryEntity?

    @Query("UPDATE categories SET isArchived = 1 WHERE id = :id")
    suspend fun archive(id: Long)

    @Query("UPDATE categories SET isArchived = 0 WHERE id = :id")
    suspend fun unarchive(id: Long)

    /**
     * Whether a name is already taken, closed categories included: closing keeps the
     * name and history still renders it, so two "Mercado" side by side — one grey —
     * is not a name. `COLLATE NOCASE` makes it case-insensitive; [ignoreId] lets an
     * edit keep its own name.
     */
    @Query("SELECT EXISTS(SELECT 1 FROM categories WHERE name = :name COLLATE NOCASE AND id != :ignoreId)")
    suspend fun existsByName(name: String, ignoreId: Long): Boolean

    @Insert
    suspend fun insert(category: CategoryEntity): Long

    @Update
    suspend fun update(category: CategoryEntity)

    @Delete
    suspend fun delete(category: CategoryEntity)
}
