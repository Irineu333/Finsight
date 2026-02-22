package com.neoutils.finsight.ui.screen.dashboard

sealed class DashboardAction {
    data class AdjustBalance(
        val target: Double
    ) : DashboardAction()
}