package com.neoutils.finance.data

import kotlinx.coroutines.flow.Flow

class CategoryRepository(
    private val dao: CategoryDao
) {
    fun getAllCategories(): Flow<List<Category>> {
        return dao.getAllCategories()
    }

    fun getCategoriesByType(type: Category.CategoryType): Flow<List<Category>> {
        return dao.getCategoriesByType(type)
    }

    suspend fun getCategoryById(id: Long): Category? {
        return dao.getCategoryById(id)
    }

    suspend fun insert(category: Category) {
        dao.insert(category)
    }

    suspend fun update(category: Category) {
        dao.update(category)
    }

    suspend fun delete(category: Category) {
        dao.delete(category)
    }
}
