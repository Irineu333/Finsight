package com.neoutils.finance.domain.errors

data class CreateInvoiceErrors(
    val creditCardNotFound: String = "Credit card not found"
)
