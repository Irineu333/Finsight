package com.neoutils.finsight.ui.modal.editTransaction

import com.neoutils.finsight.util.UiText

sealed class EditTransactionEvent {
    data class ShowError(val message: UiText) : EditTransactionEvent()
}
