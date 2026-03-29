package com.neoutils.finsight.domain.repository

import com.neoutils.finsight.domain.model.DashboardComponentPreference
import kotlinx.coroutines.flow.Flow

interface IDashboardPreferencesRepository {
    fun observe(): Flow<List<DashboardComponentPreference>>
    suspend fun save(preferences: List<DashboardComponentPreference>)
}