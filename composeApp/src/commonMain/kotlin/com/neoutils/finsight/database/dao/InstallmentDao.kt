package com.neoutils.finsight.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.neoutils.finsight.database.entity.InstallmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InstallmentDao {
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
}
