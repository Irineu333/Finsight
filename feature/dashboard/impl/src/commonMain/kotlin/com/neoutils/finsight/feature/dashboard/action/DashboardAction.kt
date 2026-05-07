package com.neoutils.finsight.feature.dashboard.action

sealed class DashboardAction {
    data object EnterEditMode : DashboardAction()
    data object ConfirmEdit : DashboardAction()
    data object CancelEdit : DashboardAction()
    data object RemoveAllComponents : DashboardAction()
    data object AddAllComponents : DashboardAction()
    data class MoveComponent(val fromKey: String, val toKey: String) : DashboardAction()
    data class UpdateComponentConfig(val key: String, val config: Map<String, String>) : DashboardAction()
}