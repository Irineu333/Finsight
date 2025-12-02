package com.neoutils.finance.data

import com.neoutils.finance.data.mapper.toDomain
import com.neoutils.finance.data.mapper.toEntity
import com.neoutils.finance.domain.model.Transaction
import com.neoutils.finance.domain.repository.ITransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

class TransactionRepository(
    private val dao: TransactionDao
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
        return dao.getAllTransactions().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getTransactionById(id: Long): Transaction? {
        return dao.getTransactionById(id)?.toDomain()
    }

    override fun observeTransactionById(id: Long): Flow<Transaction?> {
        return dao.observeTransactionById(id).map { it?.toDomain() }
    }

    override fun getTransactionsByType(type: Transaction.Type): Flow<List<Transaction>> {
        return dao.getTransactionsByType(type.toEntity()).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getTransactionByTypeAndDate(type: Transaction.Type, date: LocalDate): Transaction? {
        return dao.getTransactionByTypeAndDate(type.toEntity(), date)?.toDomain()
    }

    override suspend fun deleteAll() {
        dao.deleteAll()
    }
}