package com.neoutils.finsight.feature.transactions.modal.viewTransaction

sealed class ViewOperationAction {
    data class OpenRecurring(val recurringId: Long) : ViewOperationAction()
}
