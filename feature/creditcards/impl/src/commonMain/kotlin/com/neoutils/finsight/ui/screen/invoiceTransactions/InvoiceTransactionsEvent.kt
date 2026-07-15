package com.neoutils.finsight.ui.screen.invoiceTransactions

sealed interface InvoiceTransactionsEvent {
    data object CreditCardDeleted : InvoiceTransactionsEvent
}
