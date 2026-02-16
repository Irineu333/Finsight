package com.neoutils.finance.ui.screen.installments

sealed class InstallmentsAction {
    data class SelectInstallment(val index: Int) : InstallmentsAction()
}
