package com.neoutils.finsight.ui.modal.editInvoiceBalance

import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Invoice

sealed class EditInvoiceBalanceAction {
    data class SelectCreditCard(val creditCard: CreditCard) : EditInvoiceBalanceAction()
    data class SelectInvoice(val invoice: Invoice) : EditInvoiceBalanceAction()
    data class Submit(val targetBalance: Double) : EditInvoiceBalanceAction()
}
