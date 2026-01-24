package com.neoutils.finance.domain.error

data class OpenInvoiceErrors(
    val creditCardNotFound: String = "Credit card not found",
    val overlappingInvoice: String = "Invoice period overlaps with existing invoice"
)
