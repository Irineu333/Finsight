package com.neoutils.finsight.feature.dashboard.repository

import com.neoutils.finsight.feature.dashboard.model.DashboardComponentPreference
import kotlinx.coroutines.flow.StateFlow
import com.neoutils.finsight.feature.dashboard.repository.IDashboardPreferencesRepository

interface IDashboardPreferencesRepository {
    fun observe(): StateFlow<List<DashboardComponentPreference>?>
    suspend fun save(preferences: List<DashboardComponentPreference>)
    fun observeEditTipDismissed(): StateFlow<Boolean>
    suspend fun dismissEditTip()
}
