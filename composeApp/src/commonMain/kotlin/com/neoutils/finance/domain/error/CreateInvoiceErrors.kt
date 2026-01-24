package com.neoutils.finance.domain.error

data class CreateInvoiceErrors(
    val creditCardNotFound: String = "Credit card not found"
)
