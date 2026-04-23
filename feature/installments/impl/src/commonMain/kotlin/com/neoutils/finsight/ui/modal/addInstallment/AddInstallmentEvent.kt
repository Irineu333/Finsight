package com.neoutils.finsight.ui.modal.addInstallment

import com.neoutils.finsight.util.UiText

sealed class AddInstallmentEvent {
    data class ShowError(val message: UiText) : AddInstallmentEvent()
}
