package com.neoutils.finance.domain.errors

data class ReopenInvoiceErrors(
    val invoiceNotFound: String = "Invoice not found",
    val invoiceAlreadyOpen: String = "Invoice is already open",
    val cannotReopenPaidInvoice: String = "Cannot reopen a paid invoice"
)
