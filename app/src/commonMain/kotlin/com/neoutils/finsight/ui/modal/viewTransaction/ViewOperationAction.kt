package com.neoutils.finsight.ui.modal.viewTransaction

sealed class ViewOperationAction {
    data class OpenRecurring(val recurringId: Long) : ViewOperationAction()
}
