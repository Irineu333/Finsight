package com.neoutils.finsight.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.neoutils.finsight.database.entity.OperationEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

@Dao
interface OperationDao {
    @Insert
    suspend fun insert(operation: OperationEntity): Long

    @Query("DELETE FROM operations WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM operations WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): OperationEntity?

    @Query("SELECT * FROM operations WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<OperationEntity?>

    @Query("SELECT * FROM operations ORDER BY date DESC, id DESC")
    fun observeAll(): Flow<List<OperationEntity>>

    @Query("SELECT * FROM operations ORDER BY date DESC, id DESC")
    suspend fun getAll(): List<OperationEntity>

    @Query("DELETE FROM operations WHERE targetCreditCardId = :creditCardId AND kind = 'TRANSACTION'")
    suspend fun deleteTransactionsByCreditCardId(creditCardId: Long)

    @Query(
        """
        SELECT * FROM operations
        WHERE (:date IS NULL OR date = :date)
          AND (:invoiceId IS NULL OR targetInvoiceId = :invoiceId)
          AND (:creditCardId IS NULL OR targetCreditCardId = :creditCardId)
          AND (:accountId IS NULL OR sourceAccountId = :accountId)
        ORDER BY date DESC, id DESC
    """
    )
    fun observeBy(
        date: LocalDate?,
        invoiceId: Long?,
        creditCardId: Long?,
        accountId: Long?,
    ): Flow<List<OperationEntity>>
}
