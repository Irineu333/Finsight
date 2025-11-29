package com.neoutils.finance.screen.dashboard

sealed class DashboardAction {
    data class AdjustBalance(
        val target: Double
    ) : DashboardAction()
}