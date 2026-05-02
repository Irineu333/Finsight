package com.neoutils.finsight.ui.modal.addInstallment

import com.neoutils.finsight.core.ui.util.UiText
sealed class AddInstallmentEvent {
    data class ShowError(val message: UiText) : AddInstallmentEvent()
}
