package com.neoutils.finance.data

import com.neoutils.finance.data.mapper.toDomain
import com.neoutils.finance.data.mapper.toEntity
import com.neoutils.finance.domain.model.Category
import com.neoutils.finance.domain.repository.ICategoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class CategoryRepository(
    private val dao: CategoryDao
) : ICategoryRepository {
    override fun getAllCategories(): Flow<List<Category>> {
        return dao.getAllCategories().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getCategoriesByType(type: Category.CategoryType): Flow<List<Category>> {
        return dao.getCategoriesByType(type.toEntity()).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getCategoryById(id: Long): Category? {
        return dao.getCategoryById(id)?.toDomain()
    }

    override fun observeCategoryById(id: Long): Flow<Category?> {
        return dao.observeCategoryById(id).map { it?.toDomain() }
    }

    override suspend fun insert(category: Category) {
        dao.insert(category.toEntity())
    }

    override suspend fun update(category: Category) {
        dao.update(category.toEntity())
    }

    override suspend fun delete(category: Category) {
        dao.delete(category.toEntity())
    }
}
