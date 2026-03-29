package com.neoutils.finsight.ui.screen.dashboard

sealed class DashboardAction {
    data class AdjustBalance(val target: Double) : DashboardAction()

    data object EnterEditMode : DashboardAction()
    data object ConfirmEdit : DashboardAction()
    data object CancelEdit : DashboardAction()
    data class MoveComponent(val from: Int, val to: Int) : DashboardAction()
    data class RemoveComponent(val key: String) : DashboardAction()
    data class AddComponent(val key: String, val insertAt: Int? = null) : DashboardAction()
    data class UpdateComponentConfig(val key: String, val config: Map<String, String>) : DashboardAction()
}