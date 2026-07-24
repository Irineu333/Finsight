package com.neoutils.finsight.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.neoutils.finsight.database.entity.DimensionEntity
import com.neoutils.finsight.domain.model.DimensionKind

@Dao
interface DimensionDao {

    @Insert
    suspend fun insert(dimension: DimensionEntity): Long

    suspend fun emit(kind: DimensionKind): Long = insert(DimensionEntity(kind = kind))

    @Query("SELECT * FROM dimensions WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): DimensionEntity?

    /**
     * Removing the dimension is what detaches the legs that carried it: the
     * `ON DELETE SET NULL` on `entries.dimensionId` does the rest. It replaces the
     * cascade that used to come from `entries.invoiceId → invoices`.
     */
    @Query("DELETE FROM dimensions WHERE id = :id")
    suspend fun deleteById(id: Long)
}
