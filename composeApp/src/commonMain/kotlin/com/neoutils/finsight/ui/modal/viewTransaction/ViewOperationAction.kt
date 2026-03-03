package com.neoutils.finsight.ui.modal.viewTransaction

sealed class ViewOperationAction {
    data object OpenRecurring : ViewOperationAction()
}
