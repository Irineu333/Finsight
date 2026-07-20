package com.neoutils.finsight.database.repository

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.neoutils.finsight.database.AppDatabase
import com.neoutils.finsight.database.dao.AccountDao
import com.neoutils.finsight.database.dao.CategoryDao
import com.neoutils.finsight.database.entity.AccountEntity
import com.neoutils.finsight.database.mapper.CategoryMapper
import com.neoutils.finsight.domain.model.BASE_CURRENCY
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.repository.ICategoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class CategoryRepository(
    private val database: AppDatabase,
    private val dao: CategoryDao,
    private val accountDao: AccountDao,
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

    override fun observeCategoriesByType(type: Category.Type): Flow<List<Category>> {
        return dao.observeCategoriesByType(
            mapper.toEntity(type)
        ).map { entities ->
            entities.map { mapper.toDomain(it) }
        }
    }

    override suspend fun getCategoryById(id: Long): Category? {
        return dao.getCategoryWithArchivalById(id)?.let { mapper.toDomain(it) }
    }

    override fun observeCategoryById(id: Long): Flow<Category?> {
        return dao.observeCategoryWithArchivalById(id).map { row -> row?.let { mapper.toDomain(it) } }
    }

    /**
     * A category *is* an `INCOME`/`EXPENSE` account wearing a facade, so the two
     * are created together, in one transaction. Creating the account lazily — on
     * the first transaction that used the category — meant a category could exist
     * without one, which every reader then had to special-case.
     */
    override suspend fun insert(category: Category) {
        database.useWriterConnection { connection ->
            connection.immediateTransaction {
                val accountId = accountDao.insert(category.toAccountEntity())
                dao.insert(mapper.toEntity(category).copy(accountId = accountId))
            }
        }
    }

    /** The facade is a projection: renaming or re-icon-ing it moves its account too. */
    override suspend fun update(category: Category) {
        database.useWriterConnection { connection ->
            connection.immediateTransaction {
                dao.update(mapper.toEntity(category))
                accountDao.getAccountById(category.accountId)?.let { account ->
                    accountDao.update(
                        account.copy(name = category.name, iconKey = category.icon.key)
                    )
                }
            }
        }
    }

    private fun Category.toAccountEntity() = AccountEntity(
        name = name,
        type = when (type) {
            Category.Type.INCOME -> AccountEntity.Type.INCOME
            Category.Type.EXPENSE -> AccountEntity.Type.EXPENSE
        },
        currency = BASE_CURRENCY,
        iconKey = icon.key,
        createdAt = createdAt,
    )

    /**
     * Removes the facade **and** its ledger account, in that order and as one unit.
     * The account cannot go first: `categories.accountId` is `NO ACTION`, so the row
     * still pointing at it makes the delete fail on the foreign key.
     */
    /**
     * Removes the facade **and** its ledger account, in that order and in one
     * transaction. The order matters: `categories.accountId` references the account
     * with `NO_ACTION`, so removing the account first violates the key.
     */
    override suspend fun delete(category: Category) {
        database.useWriterConnection { connection ->
            connection.immediateTransaction {
                dao.delete(mapper.toEntity(category))
                accountDao.getAccountById(category.accountId)?.let { accountDao.delete(it) }
            }
        }
    }
}
