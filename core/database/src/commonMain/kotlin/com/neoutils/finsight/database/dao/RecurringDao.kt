package com.neoutils.finsight.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.neoutils.finsight.database.entity.RecurringEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecurringDao {

    /**
     * Clears the recurring half of the pair on every transaction that names this
     * template — the nullification the dropped foreign key used to grant, now with
     * an explicit owner (design D12). It writes `transactions` from the facade's
     * DAO because a ledger DAO may not consult those columns at all, and it also
     * does more than the key did: the key never cleared `recurringCycle`.
     */
    @Query(
        """
        UPDATE transactions
        SET recurringId = NULL, recurringCycle = NULL
        WHERE recurringId = :recurringId
        """
    )
    suspend fun detachTransactions(recurringId: Long)

    @Query("SELECT * FROM recurring ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<RecurringEntity>>

    @Query("SELECT * FROM recurring ORDER BY createdAt ASC")
    suspend fun getAll(): List<RecurringEntity>

    // The FKs are SET_NULL, so deleting the account or card would silently strip
    // the link instead of failing. These back the guard that refuses the delete
    // while a template still points here.
    @Query("SELECT COUNT(*) FROM recurring WHERE accountId = :accountId")
    suspend fun countByAccount(accountId: Long): Int

    @Query("SELECT COUNT(*) FROM recurring WHERE creditCardId = :creditCardId")
    suspend fun countByCreditCard(creditCardId: Long): Int

    @Query("SELECT COUNT(*) FROM recurring WHERE categoryId = :categoryId")
    suspend fun countByCategory(categoryId: Long): Int

    @Insert
    suspend fun insert(entity: RecurringEntity): Long

    @Update
    suspend fun update(entity: RecurringEntity)

    @Delete
    suspend fun delete(entity: RecurringEntity)
}
