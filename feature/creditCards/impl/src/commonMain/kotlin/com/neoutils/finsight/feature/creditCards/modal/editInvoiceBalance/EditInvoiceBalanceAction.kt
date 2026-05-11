package com.neoutils.finsight.feature.creditCards.modal.editInvoiceBalance

sealed class EditInvoiceBalanceAction {
    data class SelectCreditCard(val creditCardId: Long) : EditInvoiceBalanceAction()
    data class SelectInvoice(val invoiceId: Long) : EditInvoiceBalanceAction()
    data class Submit(val targetBalance: Double) : EditInvoiceBalanceAction()
}
