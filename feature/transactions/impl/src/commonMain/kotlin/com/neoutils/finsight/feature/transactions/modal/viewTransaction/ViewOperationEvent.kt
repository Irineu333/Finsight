package com.neoutils.finsight.feature.transactions.modal.viewTransaction

import com.neoutils.finsight.feature.recurring.model.Recurring
sealed class ViewOperationEvent {
    data class OpenRecurring(val recurring: Recurring) : ViewOperationEvent()
}
