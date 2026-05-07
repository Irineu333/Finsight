package com.neoutils.finsight.feature.transactions.modal.viewTransaction

sealed class ViewOperationEvent {
    data class OpenRecurring(val recurringId: Long) : ViewOperationEvent()
}
