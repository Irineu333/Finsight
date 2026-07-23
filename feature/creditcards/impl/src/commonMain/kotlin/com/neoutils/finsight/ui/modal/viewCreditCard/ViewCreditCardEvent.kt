package com.neoutils.finsight.ui.modal.viewCreditCard

sealed class ViewCreditCardEvent {
    data object Dismiss : ViewCreditCardEvent()
}
