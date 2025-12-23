package com.neoutils.finance.ui.screen.creditCards

import com.neoutils.finance.domain.model.CreditCard
import com.neoutils.finance.domain.model.Invoice
import com.neoutils.finance.ui.model.InvoiceUi

data class CreditCardsUiState(
    val creditCards: List<CreditCardUi> = emptyList()
)

data class CreditCardUi(
    val creditCard: CreditCard,
    val invoiceUi: InvoiceUi?,
)

