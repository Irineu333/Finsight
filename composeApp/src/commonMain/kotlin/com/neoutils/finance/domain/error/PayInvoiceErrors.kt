package com.neoutils.finance.domain.error

data class PayInvoiceErrors(
    val invoiceNotFound: String = "Invoice not found",
    val invoiceAlreadyPaid: String = "Invoice is already paid",
    val cannotPayOpenInvoice: String = "Only closed invoices can be paid",
    val paymentDateBeforeClosing: String = "Payment date cannot be before closing date",
    val paymentDateAfterDue: String = "Payment date cannot be after due date",
    val paymentDateInFuture: String = "Payment date cannot be in the future",
)