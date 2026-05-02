package com.neoutils.finsight.feature.installments.modal.addInstallment

import com.neoutils.finsight.core.ui.util.UiText
sealed class AddInstallmentEvent {
    data class ShowError(val message: UiText) : AddInstallmentEvent()
}
