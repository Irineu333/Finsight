package com.neoutils.finance.domain.errors

data class PayInvoiceErrors(
    val invoiceNotFound: String = "Invoice not found",
    val invoiceAlreadyPaid: String = "Invoice is already paid",
    val cannotPayOpenInvoice: String = "Only closed invoices can be paid"
)