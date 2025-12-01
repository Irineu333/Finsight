package com.neoutils.finance.data

import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

class TransactionRepository(
    private val dao: TransactionDao
) {

    suspend fun insert(transaction: TransactionEntry): Long {
        return dao.insert(transaction)
    }

    suspend fun update(transaction: TransactionEntry) {
        dao.update(transaction)
    }

    suspend fun delete(transaction: TransactionEntry) {
        dao.delete(transaction)
    }

    fun getAllTransactions(): Flow<List<TransactionEntry>> {
        return dao.getAllTransactions()
    }

    suspend fun getTransactionById(id: Long): TransactionEntry? {
        return dao.getTransactionById(id)
    }

    fun observeTransactionById(id: Long): Flow<TransactionEntry?> {
        return dao.observeTransactionById(id)
    }

    fun getTransactionsByType(type: TransactionEntry.Type): Flow<List<TransactionEntry>> {
        return dao.getTransactionsByType(type)
    }

    suspend fun getTransactionByTypeAndDate(type: TransactionEntry.Type, date: LocalDate): TransactionEntry? {
        return dao.getTransactionByTypeAndDate(type, date)
    }

    suspend fun deleteAll() {
        dao.deleteAll()
    }
}