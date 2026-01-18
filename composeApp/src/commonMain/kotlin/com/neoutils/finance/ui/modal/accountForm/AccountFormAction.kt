package com.neoutils.finance.ui.modal.accountForm

sealed class AccountFormAction {

    data class NameChanged(
        val name: String
    ) : AccountFormAction()

    data class IsDefaultChanged(
        val isDefault: Boolean
    ) : AccountFormAction()

    data object Submit : AccountFormAction()
}
