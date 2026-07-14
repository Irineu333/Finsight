package com.neoutils.finsight.ui.modal.viewBudget

sealed class ViewBudgetEvent {
    data object Dismiss : ViewBudgetEvent()
}
