package com.neoutils.finsight.feature.transactions.repository

import com.neoutils.finsight.core.database.dao.TransactionDao
import com.neoutils.finsight.feature.transactions.model.Transaction
import com.neoutils.finsight.feature.transactions.mapper.TransactionMapper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

class TransactionRepository(
    private val dao: TransactionDao,
    private val mapper: TransactionMapper,
) : ITransactionRepository {

    override fun observeAllTransactions(): Flow<List<Transaction>> {
        return dao.observeAllTransactions().map { entities ->
            entities.map(mapper::toDomain)
        }
    }

    override suspend fun getAllTransactions(): List<Transaction> {
        return dao.getAllTransactions().map(mapper::toDomain)
    }

    override fun observeTransactionById(id: Long): Flow<Transaction?> {
        return dao.observeTransactionById(id).map { entity ->
            entity?.let(mapper::toDomain)
        }
    }

    override suspend fun getTransactionBy(id: Long): Transaction? {
        return dao.getTransactionById(id)?.let(mapper::toDomain)
    }

    override suspend fun getTransactionsBy(
        type: Transaction.Type?,
        target: Transaction.Target?,
        date: LocalDate?,
        invoiceId: Long?,
        accountId: Long?,
    ): List<Transaction> {
        return dao.getTransactionsBy(
            type = type?.let { mapper.toEntity(it) },
            target = target?.let { mapper.toEntity(it) },
            date = date,
            invoiceId = invoiceId,
            accountId = accountId,
        ).map(mapper::toDomain)
    }

    override suspend fun getTransactionsByCategoryAndDateRange(
        categoryId: Long,
        startDate: LocalDate,
        endDate: LocalDate,
    ): List<Transaction> {
        return dao.getTransactionsByCategoryAndDateRange(
            categoryId = categoryId,
            startDate = startDate,
            endDate = endDate,
        ).map(mapper::toDomain)
    }

    override suspend fun getTransactionsByCategoryIdsAndDateRange(
        categoryIds: List<Long>,
        startDate: LocalDate,
        endDate: LocalDate,
    ): List<Transaction> {
        if (categoryIds.isEmpty()) return emptyList()
        return dao.getTransactionsByCategoryIdsAndDateRange(
            categoryIds = categoryIds,
            startDate = startDate,
            endDate = endDate,
        ).map(mapper::toDomain)
    }

    override fun observeTransactionsBy(
        type: Transaction.Type?,
        target: Transaction.Target?,
        date: LocalDate?,
        invoiceId: Long?,
        creditCardId: Long?,
        accountId: Long?,
    ): Flow<List<Transaction>> {
        return dao.observeTransactionsBy(
            type = type?.let { mapper.toEntity(it) },
            target = target?.let { mapper.toEntity(it) },
            date = date,
            invoiceId = invoiceId,
            creditCardId = creditCardId,
            accountId = accountId,
        ).map { entities -> entities.map(mapper::toDomain) }
    }

    override suspend fun insert(transaction: Transaction): Long {
        return dao.insert(mapper.toEntity(transaction))
    }

    override suspend fun update(transaction: Transaction) {
        dao.update(mapper.toEntity(transaction))
    }

    override suspend fun delete(transaction: Transaction) {
        dao.delete(mapper.toEntity(transaction))
    }
}
