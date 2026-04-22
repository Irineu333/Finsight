package com.neoutils.finsight.domain.repository

import com.neoutils.finsight.domain.model.RecurringOccurrence
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.YearMonth

interface IRecurringOccurrenceRepository {
    fun observeAllOccurrences(): Flow<List<RecurringOccurrence>>
    suspend fun getAllOccurrences(): List<RecurringOccurrence>
    suspend fun getOccurrenceBy(recurringId: Long, yearMonth: YearMonth): RecurringOccurrence?
    suspend fun getOccurrenceBy(recurringId: Long, cycleNumber: Int): RecurringOccurrence?
    suspend fun save(occurrence: RecurringOccurrence): Long
}
