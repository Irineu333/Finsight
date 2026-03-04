package com.neoutils.finsight.ui.modal.viewTransaction

import com.neoutils.finsight.domain.model.Recurring

sealed class ViewOperationEvent {
    data class OpenRecurring(val recurring: Recurring) : ViewOperationEvent()
}
