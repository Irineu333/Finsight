package com.neoutils.finance.ui.screen.invoiceTransactions

import com.neoutils.finance.domain.model.Category
import com.neoutils.finance.domain.model.Transaction

sealed class InvoiceTransactionsAction {
    data class SelectInvoice(val index: Int) : InvoiceTransactionsAction()
    data class SelectCategory(val category: Category?) : InvoiceTransactionsAction()
    data class SelectType(val type: Transaction.Type?) : InvoiceTransactionsAction()
}
