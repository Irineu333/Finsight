package com.neoutils.finsight.ui.modal.transferBetweenAccounts

import com.neoutils.finsight.core.ui.util.UiText
sealed class TransferBetweenAccountsEvent {
    data class ShowError(val message: UiText) : TransferBetweenAccountsEvent()
}
