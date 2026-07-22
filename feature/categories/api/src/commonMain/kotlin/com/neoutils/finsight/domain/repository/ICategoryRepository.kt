package com.neoutils.finsight.domain.repository

import com.neoutils.finsight.domain.model.Category
import kotlinx.coroutines.flow.Flow

interface ICategoryRepository {
    fun observeAllCategories(): Flow<List<Category>>
    suspend fun getAllCategories(): List<Category>

    /**
     * Every category, closed ones included, each carrying its closure flag. The
     * reads above are the *active* facade, for selectors; this one is for
     * rendering history, which must keep showing a category that was later closed.
     */
    suspend fun getAllCategoriesIncludingClosed(): List<Category>

    fun observeAllCategoriesIncludingClosed(): Flow<List<Category>>
    fun observeCategoriesByType(type: Category.Type): Flow<List<Category>>
    suspend fun getCategoryById(id: Long): Category?

    /**
     * The category a ledger dimension belongs to — the inverse of the link the
     * facade keeps. Five screens were resolving this by scanning every category,
     * which is the kind of thing that reads fine and then runs once per row.
     */
    suspend fun getCategoryByDimensionId(dimensionId: Long): Category?
    fun observeCategoryById(id: Long): Flow<Category?>
    /** Retires the category on its own facade — it owns no account to close (D4). */
    suspend fun archive(id: Long)

    suspend fun insert(category: Category)
    suspend fun update(category: Category)
    suspend fun delete(category: Category)
}
