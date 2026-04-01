package com.neoutils.finsight.ui.screen.dashboard

import com.neoutils.finsight.domain.model.DashboardComponentPreference
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.component_accounts_overview
import com.neoutils.finsight.resources.component_balance_stats
import com.neoutils.finsight.resources.component_credit_card_balance_stats
import com.neoutils.finsight.resources.component_credit_cards
import com.neoutils.finsight.resources.component_budgets
import com.neoutils.finsight.resources.component_pending_balance
import com.neoutils.finsight.resources.component_pending_recurring
import com.neoutils.finsight.resources.component_quick_actions
import com.neoutils.finsight.resources.component_recents
import com.neoutils.finsight.resources.component_income_by_category
import com.neoutils.finsight.resources.component_spending_by_category
import com.neoutils.finsight.resources.component_total_balance
import com.neoutils.finsight.util.UiText

data class DashboardRegistryEntry(
    val key: String,
    val title: UiText,
    val defaultPosition: Int,
)

object DashboardComponentRegistry {

    val entries: List<DashboardRegistryEntry> = listOf(
        DashboardRegistryEntry(DashboardComponentKey.TOTAL_BALANCE.value,          UiText.Res(Res.string.component_total_balance),     0),
        DashboardRegistryEntry(DashboardComponentKey.CONCRETE_BALANCE_STATS.value, UiText.Res(Res.string.component_balance_stats),     1),
        DashboardRegistryEntry(DashboardComponentKey.PENDING_BALANCE_STATS.value,  UiText.Res(Res.string.component_pending_balance),   2),
        DashboardRegistryEntry(DashboardComponentKey.CREDIT_CARD_BALANCE_STATS.value, UiText.Res(Res.string.component_credit_card_balance_stats), 3),
        DashboardRegistryEntry(DashboardComponentKey.ACCOUNTS_OVERVIEW.value,     UiText.Res(Res.string.component_accounts_overview), 4),
        DashboardRegistryEntry(DashboardComponentKey.CREDIT_CARDS_PAGER.value,     UiText.Res(Res.string.component_credit_cards),      5),
        DashboardRegistryEntry(DashboardComponentKey.SPENDING_BY_CATEGORY.value,   UiText.Res(Res.string.component_spending_by_category), 6),
        DashboardRegistryEntry(DashboardComponentKey.INCOME_BY_CATEGORY.value,     UiText.Res(Res.string.component_income_by_category),   7),
        DashboardRegistryEntry(DashboardComponentKey.BUDGETS.value,              UiText.Res(Res.string.component_budgets),          8),
        DashboardRegistryEntry(DashboardComponentKey.PENDING_RECURRING.value,     UiText.Res(Res.string.component_pending_recurring), 9),
        DashboardRegistryEntry(DashboardComponentKey.RECENTS.value,              UiText.Res(Res.string.component_recents),           10),
        DashboardRegistryEntry(DashboardComponentKey.QUICK_ACTIONS.value,         UiText.Res(Res.string.component_quick_actions),     11),
    )

    private val defaultTopSpacingKeys = setOf(
        DashboardComponentKey.ACCOUNTS_OVERVIEW.value,
        DashboardComponentKey.CREDIT_CARDS_PAGER.value,
        DashboardComponentKey.SPENDING_BY_CATEGORY.value,
        DashboardComponentKey.INCOME_BY_CATEGORY.value,
        DashboardComponentKey.BUDGETS.value,
        DashboardComponentKey.PENDING_RECURRING.value,
        DashboardComponentKey.RECENTS.value,
        DashboardComponentKey.QUICK_ACTIONS.value,
    )

    private val defaultDisabledKeys = setOf(
        DashboardComponentKey.CREDIT_CARD_BALANCE_STATS.value,
        DashboardComponentKey.INCOME_BY_CATEGORY.value,
    )

    fun defaultConfigFor(key: String): Map<String, String> = buildMap {
        if (key in defaultTopSpacingKeys) {
            put(DashboardComponentConfig.TOP_SPACING, "true")
        }
        if (key == DashboardComponentKey.QUICK_ACTIONS.value) {
            put(DashboardComponentConfig.SHOW_HEADER, "false")
        }
        if (key == DashboardComponentKey.ACCOUNTS_OVERVIEW.value) {
            put(AccountsOverviewConfig.HIDE_SINGLE_ACCOUNT, "true")
        }
        if (
            key == DashboardComponentKey.PENDING_BALANCE_STATS.value ||
            key == DashboardComponentKey.CREDIT_CARD_BALANCE_STATS.value
        ) {
            put(DashboardComponentConfig.HIDE_WHEN_EMPTY, "true")
        }
    }

    fun defaultPreferences(): List<DashboardComponentPreference> =
        entries.filterNot { it.key in defaultDisabledKeys }.map { entry ->
            DashboardComponentPreference(
                key = entry.key,
                position = entry.defaultPosition,
                config = defaultConfigFor(entry.key),
            )
        }

    fun titleFor(key: String): UiText =
        entries.find { it.key == key }?.title ?: UiText.Raw(key)
}
