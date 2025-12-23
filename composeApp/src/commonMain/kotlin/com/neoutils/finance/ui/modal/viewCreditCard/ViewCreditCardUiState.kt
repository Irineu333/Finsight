package com.neoutils.finance.ui.modal.viewCreditCard

import com.neoutils.finance.domain.model.CreditCard
import com.neoutils.finance.domain.model.Invoice

data class ViewCreditCardUiState(
    val creditCard: CreditCard,
    val invoiceAmount: Double = 0.0,
    val currentInvoice: Invoice? = null
)
