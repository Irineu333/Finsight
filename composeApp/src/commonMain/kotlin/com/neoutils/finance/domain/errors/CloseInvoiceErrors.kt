package com.neoutils.finance.domain.errors

data class CloseInvoiceErrors(
    val invoiceNotFound: String = "Invoice not found",
    val cannotClosePaidInvoice: String = "Cannot close a paid invoice",
    val invoiceAlreadyClosed: String = "Invoice is already closed",
    val cannotCloseBeforeClosingMonth: String = "Cannot close invoice before the closing month",
    val negativeBalance: String = "Cannot close invoice with negative balance. Please adjust the transactions first."
)