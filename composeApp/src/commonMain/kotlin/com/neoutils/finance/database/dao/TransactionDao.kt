package com.neoutils.finance.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.neoutils.finance.database.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

@Dao
interface TransactionDao {

    @Insert
    suspend fun insert(transaction: TransactionEntity): Long

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Delete
    suspend fun delete(transaction: TransactionEntity)

    @Query("SELECT * FROM transactions ORDER BY date DESC, id DESC")
    fun observeAllTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY date DESC, id DESC")
    suspend fun getAllTransactions(): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getTransactionById(id: Long): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE id = :id")
    fun observeTransactionById(id: Long): Flow<TransactionEntity?>

    @Query("SELECT * FROM transactions WHERE type = :type ORDER BY date DESC")
    fun observeTransactionsByType(type: TransactionEntity.Type): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE type = :type AND date = :date LIMIT 1")
    suspend fun getTransactionByTypeAndDate(type: TransactionEntity.Type, date: LocalDate): TransactionEntity?

    @Query("""
        SELECT * FROM transactions
        WHERE (:type IS NULL OR type = :type)
          AND (:target IS NULL OR target = :target)
          AND (:date IS NULL OR date = :date)
          AND (:invoiceId IS NULL OR invoiceId = :invoiceId)
        ORDER BY date DESC, id DESC
    """)
    suspend fun getTransactionsBy(
        type: TransactionEntity.Type?,
        target: TransactionEntity.Target?,
        date: LocalDate?,
        invoiceId: Long?,
    ): List<TransactionEntity>

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()
}