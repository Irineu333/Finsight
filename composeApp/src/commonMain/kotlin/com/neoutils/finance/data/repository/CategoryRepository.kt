package com.neoutils.finance.data.repository

import com.neoutils.finance.data.database.CategoryDao
import com.neoutils.finance.data.mapper.CategoryMapper
import com.neoutils.finance.domain.model.Category
import com.neoutils.finance.domain.repository.ICategoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class CategoryRepository(
    private val dao: CategoryDao,
    private val mapper: CategoryMapper,
) : ICategoryRepository {
    override fun getAllCategories(): Flow<List<Category>> {
        return dao.getAllCategories().map { entities ->
            entities.map { mapper.toDomain(it) }
        }
    }

    override fun getCategoriesByType(type: Category.Type): Flow<List<Category>> {
        return dao.getCategoriesByType(
            mapper.toEntity(type)
        ).map { entities ->
            entities.map { mapper.toDomain(it) }
        }
    }

    override suspend fun getCategoryById(id: Long): Category? {
        return dao.getCategoryById(id)?.let {
            mapper.toDomain(it)
        }
    }

    override fun observeCategoryById(id: Long): Flow<Category?> {
        return dao.observeCategoryById(id).map {
            it?.let { mapper.toDomain(it) }
        }
    }

    override suspend fun insert(category: Category) {
        dao.insert(mapper.toEntity(category))
    }

    override suspend fun update(category: Category) {
        dao.update(mapper.toEntity(category))
    }

    override suspend fun delete(category: Category) {
        dao.delete(mapper.toEntity(category))
    }
}
