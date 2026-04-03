package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.DashboardComponentPreference
import com.neoutils.finsight.domain.repository.IDashboardPreferencesRepository
import com.neoutils.finsight.ui.screen.dashboard.DashboardComponentType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class GetDashboardPreferencesUseCase(
    private val repository: IDashboardPreferencesRepository,
) {
    operator fun invoke(): Flow<List<DashboardComponentPreference>> {
        return repository.observe().map { savedPrefs ->
            savedPrefs ?: defaultPreferences()
        }
    }

    private fun defaultPreferences(): List<DashboardComponentPreference> = listOf(
        DashboardComponentType.TOTAL_BALANCE,
        DashboardComponentType.CONCRETE_BALANCE_STATS,
        DashboardComponentType.PENDING_BALANCE_STATS,
        DashboardComponentType.ACCOUNTS_OVERVIEW,
        DashboardComponentType.CREDIT_CARDS_PAGER,
        DashboardComponentType.SPENDING_BY_CATEGORY,
        DashboardComponentType.BUDGETS,
        DashboardComponentType.PENDING_RECURRING,
        DashboardComponentType.RECENTS,
        DashboardComponentType.QUICK_ACTIONS,
    ).mapIndexed { index, item ->
        DashboardComponentPreference(
            key = item.key,
            position = index,
            config = item.defaultConfig,
        )
    }
}
