package com.neoutils.finsight.ui.screen.dashboard

/**
 * A flat, display-ready view of an account on the dashboard: the icon/name/default
 * flag the card renders and the ledger balance, all resolved by the builder. Carries
 * no domain graph — navigation uses [id].
 */
data class DashboardAccountUi(
    val id: Long,
    val iconKey: String,
    val name: String,
    val isDefault: Boolean,
    val balance: Double,
)
