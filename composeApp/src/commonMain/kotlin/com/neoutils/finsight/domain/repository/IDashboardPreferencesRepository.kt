package com.neoutils.finsight.domain.repository

import com.neoutils.finsight.domain.model.DashboardComponentPreference
import kotlinx.coroutines.flow.StateFlow

interface IDashboardPreferencesRepository {
    fun observe(): StateFlow<List<DashboardComponentPreference>?>
    suspend fun save(preferences: List<DashboardComponentPreference>)
}
