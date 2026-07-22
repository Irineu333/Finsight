package com.neoutils.finsight.database.repository

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.neoutils.finsight.database.AppDatabase
import com.neoutils.finsight.database.dao.CategoryDao
import com.neoutils.finsight.database.dao.DimensionDao
import com.neoutils.finsight.database.mapper.CategoryMapper
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.DimensionKind
import com.neoutils.finsight.domain.repository.ICategoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class CategoryRepository(
    private val database: AppDatabase,
    private val dao: CategoryDao,
    private val dimensionDao: DimensionDao,
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

    override suspend fun getAllCategoriesIncludingClosed(): List<Category> =
        dao.getAllCategoriesIncludingClosed().map { mapper.toDomain(it) }

    override fun observeAllCategoriesIncludingClosed(): Flow<List<Category>> =
        dao.observeAllCategoriesIncludingClosed().map { rows -> rows.map { mapper.toDomain(it) } }

    override suspend fun archive(id: Long) = dao.archive(id)

    override fun observeCategoriesByType(type: Category.Type): Flow<List<Category>> {
        return dao.observeCategoriesByType(
            mapper.toEntity(type)
        ).map { entities ->
            entities.map { mapper.toDomain(it) }
        }
    }

    override suspend fun getCategoryById(id: Long): Category? {
        return dao.getCategoryById(id)?.let { mapper.toDomain(it) }
    }

    override suspend fun getCategoryByDimensionId(dimensionId: Long): Category? =
        dao.getCategoryByDimensionId(dimensionId)?.let { mapper.toDomain(it) }

    override fun observeCategoryById(id: Long): Flow<Category?> {
        return dao.observeCategoryById(id).map { row -> row?.let { mapper.toDomain(it) } }
    }

    /**
     * A category and its ledger dimension are created together, in one transaction.
     * Emitting the dimension lazily — on the first transaction that used the
     * category — would let a category exist without one, which every reader would
     * then have to special-case.
     */
    override suspend fun insert(category: Category) {
        database.useWriterConnection { connection ->
            connection.immediateTransaction {
                val dimensionId = dimensionDao.emit(DimensionKind.CATEGORY)
                dao.insert(mapper.toEntity(category).copy(dimensionId = dimensionId))
            }
        }
    }

    /** The dimension carries no name or icon, so renaming touches the facade alone. */
    override suspend fun update(category: Category) {
        dao.update(mapper.toEntity(category))
    }

    /**
     * Removes the facade **and** its dimension, in that order and in one transaction.
     * The order matters: `categories.dimensionId` references the dimension with
     * `NO_ACTION`, so removing the dimension first violates the key.
     *
     * Removing the dimension is also what detaches whatever legs still carried it:
     * `entries.dimensionId` is `ON DELETE SET NULL`, so they become unclassified with
     * their amount and account untouched. It replaces the cascade that used to reach
     * the ledger through the category's account.
     */
    override suspend fun delete(category: Category) {
        database.useWriterConnection { connection ->
            connection.immediateTransaction {
                dao.delete(mapper.toEntity(category))
                dimensionDao.deleteById(category.dimensionId)
            }
        }
    }
}
