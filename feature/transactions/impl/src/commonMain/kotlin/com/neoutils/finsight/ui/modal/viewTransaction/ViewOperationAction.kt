package com.neoutils.finsight.ui.modal.viewTransaction

import com.neoutils.finsight.domain.model.Recurring

sealed class ViewOperationAction {
    data class OpenRecurring(val recurring: Recurring) : ViewOperationAction()
}
