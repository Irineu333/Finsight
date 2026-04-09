package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.DashboardComponentPreference
import com.neoutils.finsight.domain.repository.IDashboardPreferencesRepository
import com.neoutils.finsight.ui.screen.dashboard.AccountsOverviewConfig
import com.neoutils.finsight.ui.screen.dashboard.DashboardComponentConfig
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
        DashboardComponentPreference(
            key = DashboardComponentType.TOTAL_BALANCE.key,
            position = 0,
            config = emptyMap(),
        ),
        DashboardComponentPreference(
            key = DashboardComponentType.CONCRETE_BALANCE_STATS.key,
            position = 1,
            config = emptyMap(),
        ),
        DashboardComponentPreference(
            key = DashboardComponentType.PENDING_BALANCE_STATS.key,
            position = 2,
            config = mapOf(DashboardComponentConfig.HIDE_WHEN_EMPTY to "true"),
        ),
        DashboardComponentPreference(
            key = DashboardComponentType.CREDIT_CARD_BALANCE_STATS.key,
            position = 3,
            config = mapOf(DashboardComponentConfig.HIDE_WHEN_EMPTY to "true"),
        ),
        DashboardComponentPreference(
            key = DashboardComponentType.ACCOUNTS_OVERVIEW.key,
            position = 4,
            config = mapOf(
                DashboardComponentConfig.TOP_SPACING to "true",
                AccountsOverviewConfig.HIDE_SINGLE_ACCOUNT to "true",
            ),
        ),
        DashboardComponentPreference(
            key = DashboardComponentType.CREDIT_CARDS_PAGER.key,
            position = 5,
            config = mapOf(
                DashboardComponentConfig.TOP_SPACING to "true",
                DashboardComponentConfig.SHOW_EMPTY_STATE to "true"
            ),
        ),
        DashboardComponentPreference(
            key = DashboardComponentType.SPENDING_BY_CATEGORY.key,
            position = 6,
            config = mapOf(
                DashboardComponentConfig.TOP_SPACING to "true",
            ),
        ),
        DashboardComponentPreference(
            key = DashboardComponentType.BUDGETS.key,
            position = 7,
            config = mapOf(
                DashboardComponentConfig.TOP_SPACING to "true",
            ),
        ),
        DashboardComponentPreference(
            key = DashboardComponentType.PENDING_RECURRING.key,
            position = 8,
            config = mapOf(
                DashboardComponentConfig.TOP_SPACING to "true",
            ),
        ),
        DashboardComponentPreference(
            key = DashboardComponentType.RECENTS.key,
            position = 9,
            config = mapOf(
                DashboardComponentConfig.TOP_SPACING to "true",
            ),
        ),
        DashboardComponentPreference(
            key = DashboardComponentType.QUICK_ACTIONS.key,
            position = 10,
            config = mapOf(
                DashboardComponentConfig.TOP_SPACING to "true",
                DashboardComponentConfig.SHOW_HEADER to "true",
            ),
        ),
    )
}
