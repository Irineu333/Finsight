package com.neoutils.finsight.ui.modal.viewCurrency

sealed interface ViewCurrencyEvent {

    data object Dismiss : ViewCurrencyEvent
}
