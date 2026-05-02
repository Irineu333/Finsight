package com.neoutils.finsight.feature.creditCards.model

import com.neoutils.finsight.core.domain.model.CreditCard

data class CreditCardUi(
    val creditCard: CreditCard,
    val invoiceUi: InvoiceUi?,
)
