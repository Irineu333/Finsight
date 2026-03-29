package com.neoutils.finsight.ui.screen.dashboard

import com.neoutils.finsight.domain.model.DashboardComponentPreference
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.component_accounts_overview
import com.neoutils.finsight.resources.component_balance_stats
import com.neoutils.finsight.resources.component_credit_cards
import com.neoutils.finsight.resources.component_pending_balance
import com.neoutils.finsight.resources.component_pending_recurring
import com.neoutils.finsight.resources.component_quick_actions
import com.neoutils.finsight.resources.component_recents
import com.neoutils.finsight.resources.component_spending
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
        DashboardRegistryEntry(DashboardComponent.AccountsOverview.KEY,     UiText.Res(Res.string.component_accounts_overview), 3),
        DashboardRegistryEntry(DashboardComponent.CreditCardsPager.KEY,     UiText.Res(Res.string.component_credit_cards),      4),
        DashboardRegistryEntry(DashboardComponent.SpendingPager.KEY,        UiText.Res(Res.string.component_spending),          5),
        DashboardRegistryEntry(DashboardComponent.PendingRecurring.KEY,     UiText.Res(Res.string.component_pending_recurring), 6),
        DashboardRegistryEntry(DashboardComponent.Recents.KEY,              UiText.Res(Res.string.component_recents),           7),
        DashboardRegistryEntry(DashboardComponent.QuickActions.KEY,         UiText.Res(Res.string.component_quick_actions),     8),
    )

    fun defaultPreferences(): List<DashboardComponentPreference> =
        entries.map { DashboardComponentPreference(it.key, it.defaultPosition) }

    fun titleFor(key: String): UiText =
        entries.find { it.key == key }?.title ?: UiText.Raw(key)
}
