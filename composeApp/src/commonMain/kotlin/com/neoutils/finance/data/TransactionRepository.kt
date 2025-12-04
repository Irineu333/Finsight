package com.neoutils.finance.data

import com.neoutils.finance.data.mapper.toDomain
import com.neoutils.finance.data.mapper.toEntity
import com.neoutils.finance.domain.model.Transaction
import com.neoutils.finance.domain.repository.ICategoryRepository
import com.neoutils.finance.domain.repository.ITransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.LocalDate

class TransactionRepository(
    private val dao: TransactionDao,
    private val categoryRepository: ICategoryRepository,
) : ITransactionRepository {

    override suspend fun insert(transaction: Transaction): Long {
        return dao.insert(transaction.toEntity())
    }

    override suspend fun update(transaction: Transaction) {
        dao.update(transaction.toEntity())
    }

    override suspend fun delete(transaction: Transaction) {
        dao.delete(transaction.toEntity())
    }

    override fun getAllTransactions(): Flow<List<Transaction>> {
        return combine(
            dao.getAllTransactions(),
            categoryRepository.getAllCategories()
        ) { transactions, categories ->
            transactions.map { transaction ->
                transaction.toDomain(
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

        return transaction.toDomain(category)
    }

    override fun observeTransactionById(id: Long): Flow<Transaction?> {
        return combine(
            dao.observeTransactionById(id),
            categoryRepository.observeCategoryById(id)
        ) { transaction, category ->
            transaction?.toDomain(category)
        }
    }

    override fun getTransactionsByType(type: Transaction.Type): Flow<List<Transaction>> {
        return combine(
            dao.getTransactionsByType(type.toEntity()),
            categoryRepository.getAllCategories(),
        ) { transactions, categories ->
            transactions.map { transaction ->
                transaction.toDomain(
                    category = categories.find {
                        it.id == transaction.categoryId
                    }
                )
            }
        }
    }

    override suspend fun getTransactionByTypeAndDate(type: Transaction.Type, date: LocalDate): Transaction? {
        val transaction = dao.getTransactionByTypeAndDate(type.toEntity(), date) ?: return null

        val category = transaction.categoryId?.let {
            categoryRepository.getCategoryById(it)
        }

        return transaction.toDomain(category)
    }

    override suspend fun deleteAll() {
        dao.deleteAll()
    }
}