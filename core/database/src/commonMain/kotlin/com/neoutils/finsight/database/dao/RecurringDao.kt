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

    @Insert
    suspend fun insert(entity: RecurringEntity): Long

    @Update
    suspend fun update(entity: RecurringEntity)

    @Delete
    suspend fun delete(entity: RecurringEntity)
}
