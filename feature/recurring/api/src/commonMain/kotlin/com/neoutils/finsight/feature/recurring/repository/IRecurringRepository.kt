package com.neoutils.finsight.feature.recurring.repository

import com.neoutils.finsight.feature.recurring.model.Recurring
import kotlinx.coroutines.flow.Flow

interface IRecurringRepository {
    fun observeAllRecurring(): Flow<List<Recurring>>
    suspend fun getRecurringById(id: Long): Recurring?
    suspend fun insert(recurring: Recurring)
    suspend fun update(recurring: Recurring)
    suspend fun delete(recurring: Recurring)
}
