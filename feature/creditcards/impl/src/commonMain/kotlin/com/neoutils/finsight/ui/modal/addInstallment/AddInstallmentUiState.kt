package com.neoutils.finsight.ui.modal.addInstallment

import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.InvoiceMonthSelection

data class AddInstallmentUiState(
    val categories: List<Category> = emptyList(),
    val creditCards: List<CreditCard> = emptyList(),
    val selectedCreditCard: CreditCard? = null,
    val invoiceSelection: InvoiceMonthSelection? = null,
    // What the amount being typed is denominated in: the currency of the selected
    // card (design D17). Until a card is chosen there is nothing to denominate it
    // with, and the form already refuses to submit without one.
    val currency: String? = null,
) {
    val isInvoiceBlocked = invoiceSelection?.isClosedToNewExpenses == true
}
