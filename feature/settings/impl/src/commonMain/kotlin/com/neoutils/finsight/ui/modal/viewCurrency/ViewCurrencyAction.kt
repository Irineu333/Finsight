package com.neoutils.finsight.ui.modal.viewCurrency

sealed interface ViewCurrencyAction {

    data object Unarchive : ViewCurrencyAction
}
