package com.neoutils.finsight.ui.screen.dashboard

import com.neoutils.finsight.extension.DisplayAmount

/**
 * A flat, display-ready view of an account on the dashboard: the icon/name/default
 * flag the card renders and the ledger balance, all resolved by the builder. Carries
 * no domain graph — navigation uses [id].
 *
 * [balance] is the account's own figure and is therefore denominated by the account —
 * exact, one term, and never the base currency (design D29).
 */
data class DashboardAccountUi(
    val id: Long,
    val iconKey: String,
    val name: String,
    val isDefault: Boolean,
    val balance: DisplayAmount,
)
