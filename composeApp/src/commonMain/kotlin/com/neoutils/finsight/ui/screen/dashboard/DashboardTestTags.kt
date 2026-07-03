package com.neoutils.finsight.ui.screen.dashboard

object DashboardTestTags {
    const val ROOT = "dashboard-root"
    const val TOTAL_BALANCE = "dashboard-total-balance"
    const val ADD_ACCOUNT = "dashboard-add-account"

    fun accountBalance(accountId: Long): String = "dashboard-account-balance-$accountId"

    fun quickAction(type: QuickActionType): String = "dashboard-quick-action-${type.tag}"
}
