package com.neoutils.finsight.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.neoutils.finsight.database.entity.RecurringOccurrenceEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.YearMonth

@Dao
interface RecurringOccurrenceDao {

    @Query("SELECT * FROM recurring_occurrences")
    fun observeAll(): Flow<List<RecurringOccurrenceEntity>>

    @Query("SELECT * FROM recurring_occurrences")
    suspend fun getAll(): List<RecurringOccurrenceEntity>

    @Query(
        """
        SELECT * FROM recurring_occurrences
        WHERE recurringId = :recurringId AND yearMonth = :yearMonth
        LIMIT 1
        """
    )
    suspend fun getByRecurringAndMonth(
        recurringId: Long,
        yearMonth: YearMonth,
    ): RecurringOccurrenceEntity?

    @Query(
        """
        SELECT * FROM recurring_occurrences
        WHERE recurringId = :recurringId AND cycleNumber = :cycleNumber
        LIMIT 1
        """
    )
    suspend fun getByRecurringAndCycle(
        recurringId: Long,
        cycleNumber: Int,
    ): RecurringOccurrenceEntity?

    @Insert
    suspend fun insert(entity: RecurringOccurrenceEntity): Long

    @Update
    suspend fun update(entity: RecurringOccurrenceEntity)
}
