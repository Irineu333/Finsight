package com.neoutils.finsight.feature.creditCards.modal.editInvoiceBalance

import com.neoutils.finsight.feature.creditCards.model.CreditCard
import com.neoutils.finsight.feature.creditCards.model.Invoice

sealed class EditInvoiceBalanceAction {
    data class SelectCreditCard(val creditCard: CreditCard) : EditInvoiceBalanceAction()
    data class SelectInvoice(val invoice: Invoice) : EditInvoiceBalanceAction()
    data class Submit(val targetBalance: Double) : EditInvoiceBalanceAction()
}
