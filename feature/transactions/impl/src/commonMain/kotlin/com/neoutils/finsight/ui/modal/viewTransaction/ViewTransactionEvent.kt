package com.neoutils.finsight.ui.modal.viewTransaction

import com.neoutils.finsight.domain.model.Recurring

sealed class ViewTransactionEvent {
    data object Dismiss : ViewTransactionEvent()
    data class OpenRecurring(val recurring: Recurring) : ViewTransactionEvent()
}
