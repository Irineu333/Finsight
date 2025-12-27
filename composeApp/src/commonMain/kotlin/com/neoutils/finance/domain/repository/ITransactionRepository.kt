package com.neoutils.finance.domain.repository

import com.neoutils.finance.domain.model.CreditCard
import com.neoutils.finance.domain.model.Transaction
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

interface ITransactionRepository {
    suspend fun insert(transaction: Transaction): Long
    suspend fun update(transaction: Transaction)
    suspend fun delete(transaction: Transaction)
    fun observeAllTransactions(): Flow<List<Transaction>>
    suspend fun getAllTransactions(): List<Transaction>
    suspend fun getTransactionById(id: Long): Transaction?
    fun observeTransactionById(id: Long): Flow<Transaction?>
    fun observeTransactionsByType(type: Transaction.Type): Flow<List<Transaction>>
    suspend fun getTransactionByTypeAndDate(type: Transaction.Type, date: LocalDate): Transaction?

    suspend fun getTransactionsBy(
        type: Transaction.Type?,
        target: Transaction.Target?,
        date: LocalDate?,
        invoiceId: Long?,
    ): List<Transaction>

    fun observeTransactionsBy(
        type: Transaction.Type? = null,
        target: Transaction.Target? = null,
        date: LocalDate? = null,
        invoiceId: Long? = null,
        creditCardId: Long? = null,
    ): Flow<List<Transaction>>

    suspend fun deleteAll()
}
