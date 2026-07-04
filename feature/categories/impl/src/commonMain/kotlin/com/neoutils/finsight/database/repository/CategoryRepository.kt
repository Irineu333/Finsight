package com.neoutils.finsight.database.repository

import com.neoutils.finsight.database.dao.CategoryDao
import com.neoutils.finsight.database.mapper.CategoryMapper
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.repository.ICategoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class CategoryRepository(
    private val dao: CategoryDao,
    private val mapper: CategoryMapper,
) : ICategoryRepository {
    override fun observeAllCategories(): Flow<List<Category>> {
        return dao.observeAllCategories().map { entities ->
            entities.map { mapper.toDomain(it) }
        }
    }

    override suspend fun getAllCategories(): List<Category> {
        return dao.getAllCategories().map { mapper.toDomain(it) }
    }

    override fun observeCategoriesByType(type: Category.Type): Flow<List<Category>> {
        return dao.observeCategoriesByType(
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
