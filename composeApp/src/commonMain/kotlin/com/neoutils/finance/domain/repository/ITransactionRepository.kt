package com.neoutils.finance.domain.repository

import com.neoutils.finance.domain.model.Transaction
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

interface ITransactionRepository {
    suspend fun insert(transaction: Transaction): Long
    suspend fun update(transaction: Transaction)
    suspend fun delete(transaction: Transaction)
    fun observeAllTransactions(): Flow<List<Transaction>>
    suspend fun getAllTransactions(): List<Transaction>
    fun observeTransactionById(id: Long): Flow<Transaction?>

    suspend fun getTransactionsBy(
        type: Transaction.Type? = null,
        target: Transaction.Target? = null,
        date: LocalDate? = null,
        invoiceId: Long? = null,
        accountId: Long? = null,
    ): List<Transaction>

    fun observeTransactionsBy(
        type: Transaction.Type? = null,
        target: Transaction.Target? = null,
        date: LocalDate? = null,
        invoiceId: Long? = null,
        creditCardId: Long? = null,
        accountId: Long? = null,
    ): Flow<List<Transaction>>
}
