package com.neoutils.finance.ui.modal.viewCreditCard

import com.neoutils.finance.domain.model.CreditCard
import com.neoutils.finance.domain.model.Invoice
import com.neoutils.finance.ui.model.InvoiceUi

data class ViewCreditCardUiState(
    val creditCard: CreditCard,
    val invoiceUi: InvoiceUi?,
)
