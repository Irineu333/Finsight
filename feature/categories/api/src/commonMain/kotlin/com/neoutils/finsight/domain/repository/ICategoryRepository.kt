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
    fun observeCategoryById(id: Long): Flow<Category?>
    suspend fun insert(category: Category)
    suspend fun update(category: Category)
    suspend fun delete(category: Category)
}
