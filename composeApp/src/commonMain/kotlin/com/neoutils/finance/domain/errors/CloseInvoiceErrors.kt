package com.neoutils.finance.domain.errors

data class CloseInvoiceErrors(
    val invoiceNotFound: String = "Invoice not found",
    val cannotClosePaidInvoice: String = "Cannot close a paid invoice",
    val invoiceAlreadyClosed: String = "Invoice is already closed",
    val cannotCloseOutsideClosingMonth: String = "Cannot close invoice outside of the closing month",
    val negativeBalance: String = "Cannot close invoice with negative balance. Please adjust the transactions first."
)