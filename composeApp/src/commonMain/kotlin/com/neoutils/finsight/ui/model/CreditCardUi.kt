package com.neoutils.finsight.ui.model

import com.neoutils.finsight.domain.model.CreditCard

data class CreditCardUi(
    val creditCard: CreditCard,
    val invoiceUi: InvoiceUi?,
)
