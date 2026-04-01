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
        DashboardRegistryEntry(DashboardComponent.TotalBalance.KEY,          UiText.Res(Res.string.component_total_balance),     0),
        DashboardRegistryEntry(DashboardComponent.ConcreteBalanceStats.KEY, UiText.Res(Res.string.component_balance_stats),     1),
        DashboardRegistryEntry(DashboardComponent.PendingBalanceStats.KEY,  UiText.Res(Res.string.component_pending_balance),   2),
        DashboardRegistryEntry(DashboardComponent.CreditCardBalanceStats.KEY, UiText.Res(Res.string.component_credit_card_balance_stats), 3),
        DashboardRegistryEntry(DashboardComponent.AccountsOverview.KEY,     UiText.Res(Res.string.component_accounts_overview), 4),
        DashboardRegistryEntry(DashboardComponent.CreditCardsPager.KEY,     UiText.Res(Res.string.component_credit_cards),      5),
        DashboardRegistryEntry(DashboardComponent.SpendingByCategory.KEY,   UiText.Res(Res.string.component_spending_by_category), 6),
        DashboardRegistryEntry(DashboardComponent.IncomeByCategory.KEY,     UiText.Res(Res.string.component_income_by_category),   7),
        DashboardRegistryEntry(DashboardComponent.Budgets.KEY,              UiText.Res(Res.string.component_budgets),          8),
        DashboardRegistryEntry(DashboardComponent.PendingRecurring.KEY,     UiText.Res(Res.string.component_pending_recurring), 9),
        DashboardRegistryEntry(DashboardComponent.Recents.KEY,              UiText.Res(Res.string.component_recents),           10),
        DashboardRegistryEntry(DashboardComponent.QuickActions.KEY,         UiText.Res(Res.string.component_quick_actions),     11),
    )

    private val defaultTopSpacingKeys = setOf(
        DashboardComponent.AccountsOverview.KEY,
        DashboardComponent.CreditCardsPager.KEY,
        DashboardComponent.SpendingByCategory.KEY,
        DashboardComponent.IncomeByCategory.KEY,
        DashboardComponent.Budgets.KEY,
        DashboardComponent.PendingRecurring.KEY,
        DashboardComponent.Recents.KEY,
        DashboardComponent.QuickActions.KEY,
    )

    private val defaultDisabledKeys = setOf(
        DashboardComponent.CreditCardBalanceStats.KEY,
        DashboardComponent.IncomeByCategory.KEY,
    )

    fun defaultConfigFor(key: String): Map<String, String> = buildMap {
        if (key in defaultTopSpacingKeys) {
            put(DashboardComponentConfig.TOP_SPACING, "true")
        }
        if (key == DashboardComponent.QuickActions.KEY) {
            put(DashboardComponentConfig.SHOW_HEADER, "false")
        }
        if (key == DashboardComponent.AccountsOverview.KEY) {
            put(AccountsOverviewConfig.HIDE_SINGLE_ACCOUNT, "true")
        }
        if (
            key == DashboardComponent.PendingBalanceStats.KEY ||
            key == DashboardComponent.CreditCardBalanceStats.KEY
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
