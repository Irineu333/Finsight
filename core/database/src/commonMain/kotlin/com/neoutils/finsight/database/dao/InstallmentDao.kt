package com.neoutils.finsight.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.neoutils.finsight.database.entity.InstallmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InstallmentDao {

    /**
     * The installment's own questions about the transactions that name it.
     *
     * They read and write `transactions`, a ledger table, from the facade's DAO —
     * which is the only place they can live. `transactions` retains the installment
     * columns as declared metadata (design D12), but no *ledger* read may consult
     * them, and the foreign key that used to nullify them cannot follow the table
     * into a module that cannot see `installments`. So the facade asks, and the
     * facade clears — explicitly, in the same transaction as its own removal.
     */
    @Query("SELECT COUNT(*) FROM transactions WHERE installmentId = :installmentId")
    suspend fun countTransactions(installmentId: Long): Int

    @Query(
        """
        UPDATE transactions
        SET installmentId = NULL, installmentNumber = NULL
        WHERE installmentId = :installmentId
        """
    )
    suspend fun detachTransactions(installmentId: Long)
    @Insert
    suspend fun insert(installment: InstallmentEntity): Long

    @Query("SELECT * FROM installments WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): InstallmentEntity?

    @Query("SELECT * FROM installments")
    fun observeAll(): Flow<List<InstallmentEntity>>

    @Query("SELECT * FROM installments")
    suspend fun getAll(): List<InstallmentEntity>

    @Query("DELETE FROM installments WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE installments SET count = :count, totalAmount = :totalAmount WHERE id = :id")
    suspend fun updateById(id: Long, count: Int, totalAmount: Double)
}
