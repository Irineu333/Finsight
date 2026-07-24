package com.neoutils.finsight.ui.screen.invoiceTransactions

import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.TransactionType

sealed class InvoiceTransactionsAction {
    data class SelectInvoice(val index: Int) : InvoiceTransactionsAction()
    data class SelectCategory(val category: Category?) : InvoiceTransactionsAction()
    data class SelectType(val type: TransactionType?) : InvoiceTransactionsAction()
    data class ToggleRecurring(val enabled: Boolean) : InvoiceTransactionsAction()
    data class ToggleInstallment(val enabled: Boolean) : InvoiceTransactionsAction()
    data object Unarchive : InvoiceTransactionsAction()
}
