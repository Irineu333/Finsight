package com.neoutils.finsight.ui.modal.addTransaction

import com.neoutils.finsight.util.UiText

sealed class AddTransactionEvent {
    data class ShowError(val message: UiText) : AddTransactionEvent()
}
