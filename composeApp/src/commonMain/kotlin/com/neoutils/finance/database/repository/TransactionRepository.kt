package com.neoutils.finance.database.repository

import com.neoutils.finance.database.dao.TransactionDao
import com.neoutils.finance.database.mapper.TransactionMapper
import com.neoutils.finance.domain.model.Transaction
import com.neoutils.finance.domain.repository.ICategoryRepository
import com.neoutils.finance.domain.repository.ITransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

class TransactionRepository(
    private val dao: TransactionDao,
    private val categoryRepository: ICategoryRepository,
    private val mapper: TransactionMapper = TransactionMapper(),
) : ITransactionRepository {

    override suspend fun insert(transaction: Transaction): Long {
        return dao.insert(mapper.toEntity(transaction))
    }

    override suspend fun update(transaction: Transaction) {
        dao.update(mapper.toEntity(transaction))
    }

    override suspend fun delete(transaction: Transaction) {
        dao.delete(mapper.toEntity(transaction))
    }

    override fun observeAllTransactions(): Flow<List<Transaction>> {
        return combine(
            dao.observeAllTransactions(),
            categoryRepository.getAllCategories().map { categories ->
                categories.associateBy { it.id }
            }
        ) { transactions, categories ->
            transactions.map { transaction ->
                mapper.toDomain(
                    entity = transaction,
                    category = categories[transaction.categoryId],
                )
            }
        }
    }

    override suspend fun getAllTransactions(): List<Transaction> {
        val transactions = dao.getAllTransactions()
        val categories = categoryRepository.getAllCategoriesDirect().associateBy { it.id }

        return transactions.map { transaction ->
            mapper.toDomain(
                entity = transaction,
                category = categories[transaction.categoryId],
            )
        }
    }

    override suspend fun getTransactionById(id: Long): Transaction? {
        val transaction = dao.getTransactionById(id) ?: return null

        return mapper.toDomain(
            entity = transaction,
            category = transaction.categoryId?.let {
                categoryRepository.getCategoryById(it)
            },
        )
    }

    override fun observeTransactionById(id: Long): Flow<Transaction?> {
        return dao.observeTransactionById(id).map { transaction ->
            transaction?.let { transaction ->
                mapper.toDomain(
                    entity = transaction,
                    category = transaction.categoryId?.let {
                        categoryRepository.getCategoryById(it)
                    },
                )
            }
        }
    }

    override fun getTransactionsByType(type: Transaction.Type): Flow<List<Transaction>> {
        return combine(
            dao.getTransactionsByType(mapper.toEntity(type)),
            categoryRepository.getAllCategories().map { categories ->
                categories.associateBy { it.id }
            },
        ) { transactions, categories ->
            transactions.map { transaction ->
                mapper.toDomain(
                    entity = transaction,
                    category = categories[transaction.categoryId],
                )
            }
        }
    }

    override suspend fun getTransactionByTypeAndDate(
        type: Transaction.Type,
        date: LocalDate
    ): Transaction? {
        val transaction = dao.getTransactionByTypeAndDate(mapper.toEntity(type), date) ?: return null

        return mapper.toDomain(
            entity = transaction,
            category = transaction.categoryId?.let {
                categoryRepository.getCategoryById(it)
            },
        )
    }

    override suspend fun deleteAll() {
        dao.deleteAll()
    }
}