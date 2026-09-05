package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.DashboardComponentPreference
import com.neoutils.finsight.domain.repository.IDashboardPreferencesRepository
import com.neoutils.finsight.feature.shell.api.NavCatalog
import com.neoutils.finsight.ui.screen.dashboard.AccountsOverviewConfig
import com.neoutils.finsight.ui.screen.dashboard.DashboardComponentConfig
import com.neoutils.finsight.ui.screen.dashboard.DashboardComponentType
import com.neoutils.finsight.ui.screen.dashboard.QuickActionsConfig
import com.neoutils.finsight.ui.screen.dashboard.actionKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class GetDashboardPreferencesUseCase(
    private val repository: IDashboardPreferencesRepository,
    private val navCatalog: NavCatalog,
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
        // The flow widget a fresh dashboard opens with is the neutral one: it answers
        // "how much left my money this month" over accounts *and* cards. The
        // accounts-only perimeter is one edit away, and stacking both by default would
        // put two cards showing the very same income side by side.
        DashboardComponentPreference(
            key = DashboardComponentType.OVERALL_BALANCE_STATS.key,
            position = 1,
            config = emptyMap(),
        ),
        // Takes the place `PENDING_BALANCE_STATS` held, and its `hide_when_empty` with it:
        // it contains what that widget summed — the month's untreated recurring — and adds
        // the invoices left to pay. A superset, so no dashboard reading the default loses
        // information by the swap, and nothing saved is rewritten. Both sources are absent
        // from this config on purpose: absent reads as on.
        DashboardComponentPreference(
            key = DashboardComponentType.MONTH_SETTLEMENT.key,
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
                // The grid is an affordance of the narrow window, and a narrow window happens on
                // the desktop too — so what it hides is what *this platform* does not offer, in
                // both directions, and not one of the two directions spelled out by name.
                QuickActionsConfig.HIDDEN_ACTIONS to navCatalog.destinations
                    .filterNot { it.isOffered }
                    .joinToString(",") { it.actionKey },
            ),
        ),
    )
}
