package com.neoutils.finsight.feature.categories.repository

import com.neoutils.finsight.feature.categories.model.Category
import kotlinx.coroutines.flow.Flow

interface ICategoryRepository {
    fun observeAllCategories(): Flow<List<Category>>
    suspend fun getAllCategories(): List<Category>
    fun observeCategoriesByType(type: Category.Type): Flow<List<Category>>
    suspend fun getCategoryById(id: Long): Category?
    fun observeCategoryById(id: Long): Flow<Category?>
    fun observeCategoriesByIds(ids: List<Long>): Flow<List<Category>>
    suspend fun insert(category: Category)
    suspend fun update(category: Category)
    suspend fun delete(category: Category)
}
