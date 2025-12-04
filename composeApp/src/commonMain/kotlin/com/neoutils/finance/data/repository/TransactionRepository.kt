package com.neoutils.finance.data.repository

import com.neoutils.finance.data.database.TransactionDao
import com.neoutils.finance.data.mapper.TransactionMapper
import com.neoutils.finance.domain.model.Transaction
import com.neoutils.finance.domain.repository.ICategoryRepository
import com.neoutils.finance.domain.repository.ITransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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

    override fun getAllTransactions(): Flow<List<Transaction>> {
        return combine(
            dao.getAllTransactions(),
            categoryRepository.getAllCategories()
        ) { transactions, categories ->
            transactions.map { transaction ->
                mapper.toDomain(
                    entity = transaction,
                    category = categories.find {
                        it.id == transaction.categoryId
                    }
                )
            }
        }
    }

    override suspend fun getTransactionById(id: Long): Transaction? {
        val transaction = dao.getTransactionById(id) ?: return null

        val category = transaction.categoryId?.let {
            categoryRepository.getCategoryById(it)
        }

        return mapper.toDomain(transaction, category)
    }

    override fun observeTransactionById(id: Long): Flow<Transaction?> {
        return combine(
            dao.observeTransactionById(id),
            categoryRepository.observeCategoryById(id)
        ) { transaction, category ->
            transaction?.let {
                mapper.toDomain(it, category)
            }
        }
    }

    override fun getTransactionsByType(type: Transaction.Type): Flow<List<Transaction>> {
        return combine(
            dao.getTransactionsByType(mapper.toEntity(type)),
            categoryRepository.getAllCategories(),
        ) { transactions, categories ->
            transactions.map { transaction ->
                mapper.toDomain(
                    entity = transaction,
                    category = categories.find {
                        it.id == transaction.categoryId
                    }
                )
            }
        }
    }

    override suspend fun getTransactionByTypeAndDate(
        type: Transaction.Type,
        date: LocalDate
    ): Transaction? {
        val transaction = dao.getTransactionByTypeAndDate(mapper.toEntity(type), date) ?: return null

        val category = transaction.categoryId?.let {
            categoryRepository.getCategoryById(it)
        }

        return mapper.toDomain(transaction, category)
    }

    override suspend fun deleteAll() {
        dao.deleteAll()
    }
}