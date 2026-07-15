package com.neoutils.finsight.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.neoutils.finsight.database.entity.EntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EntryDao {

    @Insert
    suspend fun insert(entry: EntryEntity): Long

    @Insert
    suspend fun insertAll(entries: List<EntryEntity>): List<Long>

    @Delete
    suspend fun delete(entry: EntryEntity)

    @Query("DELETE FROM entries WHERE operationId = :operationId")
    suspend fun deleteByOperationId(operationId: Long)

    @Query("SELECT * FROM entries ORDER BY id ASC")
    suspend fun getAll(): List<EntryEntity>

    @Query("SELECT * FROM entries ORDER BY id ASC")
    fun observeAll(): Flow<List<EntryEntity>>

    @Query("SELECT * FROM entries WHERE operationId = :operationId ORDER BY id ASC")
    suspend fun getByOperationId(operationId: Long): List<EntryEntity>

    @Query("SELECT * FROM entries WHERE accountId = :accountId ORDER BY id ASC")
    fun observeByAccountId(accountId: Long): Flow<List<EntryEntity>>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM entries WHERE accountId = :accountId AND currency = :currency")
    suspend fun naturalBalanceOf(accountId: Long, currency: String): Long
}
