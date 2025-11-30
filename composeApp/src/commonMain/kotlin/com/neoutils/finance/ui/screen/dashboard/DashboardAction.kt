package com.neoutils.finance.ui.screen.dashboard

sealed class DashboardAction {
    data class AdjustBalance(
        val target: Double
    ) : DashboardAction()
}