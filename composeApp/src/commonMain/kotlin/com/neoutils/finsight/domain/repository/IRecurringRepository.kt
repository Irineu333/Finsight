package com.neoutils.finsight.domain.repository

import com.neoutils.finsight.domain.model.Recurring
import kotlinx.coroutines.flow.Flow

interface IRecurringRepository {
    fun observeAllRecurring(): Flow<List<Recurring>>
    suspend fun insert(recurring: Recurring)
    suspend fun update(recurring: Recurring)
    suspend fun delete(recurring: Recurring)
}
