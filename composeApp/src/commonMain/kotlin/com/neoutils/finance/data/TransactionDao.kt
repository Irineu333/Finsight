package com.neoutils.finance.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Insert
    suspend fun insert(transaction: TransactionEntry): Long

    @Update
    suspend fun update(transaction: TransactionEntry)

    @Delete
    suspend fun delete(transaction: TransactionEntry)

    @Query("SELECT * FROM transactions ORDER BY date DESC, id DESC")
    fun getAllTransactions(): Flow<List<TransactionEntry>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getTransactionById(id: Long): TransactionEntry?

    @Query("SELECT * FROM transactions WHERE id = :id")
    fun observeTransactionById(id: Long): Flow<TransactionEntry?>

    @Query("SELECT * FROM transactions WHERE type = :type ORDER BY date DESC")
    fun getTransactionsByType(type: TransactionEntry.Type): Flow<List<TransactionEntry>>

    @Query("SELECT * FROM transactions WHERE type = :type AND date = :date LIMIT 1")
    suspend fun getTransactionByTypeAndDate(type: TransactionEntry.Type, date: kotlinx.datetime.LocalDate): TransactionEntry?

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()
}