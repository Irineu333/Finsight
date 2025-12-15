package com.neoutils.finance.domain.repository

import com.neoutils.finance.domain.model.Category
import kotlinx.coroutines.flow.Flow

interface ICategoryRepository {
    fun observeAllCategories(): Flow<List<Category>>
    suspend fun getAllCategories(): List<Category>
    fun getCategoriesByType(type: Category.Type): Flow<List<Category>>
    suspend fun getCategoryById(id: Long): Category?
    fun observeCategoryById(id: Long): Flow<Category?>
    suspend fun insert(category: Category)
    suspend fun update(category: Category)
    suspend fun delete(category: Category)
}
