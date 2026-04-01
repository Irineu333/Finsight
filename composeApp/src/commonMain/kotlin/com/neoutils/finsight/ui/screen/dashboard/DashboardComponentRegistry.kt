package com.neoutils.finsight.ui.screen.dashboard

import com.neoutils.finsight.domain.model.DashboardComponentPreference
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.component_accounts_overview
import com.neoutils.finsight.resources.component_balance_stats
import com.neoutils.finsight.resources.component_budgets
import com.neoutils.finsight.resources.component_credit_card_balance_stats
import com.neoutils.finsight.resources.component_credit_cards
import com.neoutils.finsight.resources.component_income_by_category
import com.neoutils.finsight.resources.component_pending_balance
import com.neoutils.finsight.resources.component_pending_recurring
import com.neoutils.finsight.resources.component_quick_actions
import com.neoutils.finsight.resources.component_recents
import com.neoutils.finsight.resources.component_spending_by_category
import com.neoutils.finsight.resources.component_total_balance
import com.neoutils.finsight.util.UiText

object DashboardComponentRegistry {

    val entries: List<DashboardRegistryEntry> = listOf(
        DashboardRegistryEntry(
            key = DashboardComponentKey.TOTAL_BALANCE.value,
            title = UiText.Res(Res.string.component_total_balance),
            defaultPosition = 0,
        ),
        DashboardRegistryEntry(
            key = DashboardComponentKey.CONCRETE_BALANCE_STATS.value,
            title = UiText.Res(Res.string.component_balance_stats),
            defaultPosition = 1,
        ),
        DashboardRegistryEntry(
            key = DashboardComponentKey.PENDING_BALANCE_STATS.value,
            title = UiText.Res(Res.string.component_pending_balance),
            defaultPosition = 2,
        ),
        DashboardRegistryEntry(
            key = DashboardComponentKey.CREDIT_CARD_BALANCE_STATS.value,
            title = UiText.Res(Res.string.component_credit_card_balance_stats),
            defaultPosition = 3,
        ),
        DashboardRegistryEntry(
            key = DashboardComponentKey.ACCOUNTS_OVERVIEW.value,
            title = UiText.Res(Res.string.component_accounts_overview),
            defaultPosition = 4,
        ),
        DashboardRegistryEntry(
            key = DashboardComponentKey.CREDIT_CARDS_PAGER.value,
            title = UiText.Res(Res.string.component_credit_cards),
            defaultPosition = 5,
        ),
        DashboardRegistryEntry(
            key = DashboardComponentKey.SPENDING_BY_CATEGORY.value,
            title = UiText.Res(Res.string.component_spending_by_category),
            defaultPosition = 6,
        ),
        DashboardRegistryEntry(
            key = DashboardComponentKey.INCOME_BY_CATEGORY.value,
            title = UiText.Res(Res.string.component_income_by_category),
            defaultPosition = 7,
        ),
        DashboardRegistryEntry(
            key = DashboardComponentKey.BUDGETS.value,
            title = UiText.Res(Res.string.component_budgets),
            defaultPosition = 8,
        ),
        DashboardRegistryEntry(
            key = DashboardComponentKey.PENDING_RECURRING.value,
            title = UiText.Res(Res.string.component_pending_recurring),
            defaultPosition = 9,
        ),
        DashboardRegistryEntry(
            key = DashboardComponentKey.RECENTS.value,
            title = UiText.Res(Res.string.component_recents),
            defaultPosition = 10,
        ),
        DashboardRegistryEntry(
            key = DashboardComponentKey.QUICK_ACTIONS.value,
            title = UiText.Res(Res.string.component_quick_actions),
            defaultPosition = 11,
        ),
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

        when (key) {
            DashboardComponentKey.QUICK_ACTIONS.value -> {
                put(DashboardComponentConfig.SHOW_HEADER, "false")
            }

            DashboardComponentKey.ACCOUNTS_OVERVIEW.value -> {
                put(AccountsOverviewConfig.HIDE_SINGLE_ACCOUNT, "true")
            }

            DashboardComponentKey.PENDING_BALANCE_STATS.value,
            DashboardComponentKey.CREDIT_CARD_BALANCE_STATS.value -> {
                put(DashboardComponentConfig.HIDE_WHEN_EMPTY, "true")
            }
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
