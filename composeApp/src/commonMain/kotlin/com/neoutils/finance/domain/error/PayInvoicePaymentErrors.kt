package com.neoutils.finance.domain.error

data class PayInvoicePaymentErrors(
    val invoiceNotFound: String = "Invoice not found",
    val negativeAmount: String = "Payment amount must be positive",
    val amountExceedsInvoice: String = "Payment amount cannot exceed invoice bill",
    val amountMustMatchInvoice: String = "Payment amount must match invoice bill exactly",
    val invoiceNotClosed: String = "Invoice must be closed before payment",
    val dateOutsideInvoicePeriod: String = "Payment date must be within invoice period",
    val dateInFuture: String = "Payment date cannot be in the future",
)
