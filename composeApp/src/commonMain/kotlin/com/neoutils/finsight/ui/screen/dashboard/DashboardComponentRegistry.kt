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
        DashboardRegistryEntry("total_balance",          UiText.Res(Res.string.component_total_balance),     0),
        DashboardRegistryEntry("balance_stats_concrete", UiText.Res(Res.string.component_balance_stats),     1),
        DashboardRegistryEntry("balance_stats_pending",  UiText.Res(Res.string.component_pending_balance),   2),
        DashboardRegistryEntry("accounts_overview",      UiText.Res(Res.string.component_accounts_overview), 3),
        DashboardRegistryEntry("credit_cards_pager",     UiText.Res(Res.string.component_credit_cards),      4),
        DashboardRegistryEntry("spending_pager",         UiText.Res(Res.string.component_spending),          5),
        DashboardRegistryEntry("pending_recurring",      UiText.Res(Res.string.component_pending_recurring), 6),
        DashboardRegistryEntry("recents",                UiText.Res(Res.string.component_recents),           7),
        DashboardRegistryEntry("quick_actions_",         UiText.Res(Res.string.component_quick_actions),     8),
    )

    fun defaultPreferences(): List<DashboardComponentPreference> =
        entries.map { DashboardComponentPreference(it.key, it.defaultPosition) }

    fun titleFor(key: String): UiText =
        entries.find { it.key == key }?.title ?: UiText.Raw(key)
}