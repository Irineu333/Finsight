package com.neoutils.finsight.feature.creditCards.screen.invoiceTransactions

import com.neoutils.finsight.feature.categories.model.Category
import com.neoutils.finsight.feature.transactions.model.Transaction

sealed class InvoiceTransactionsAction {
    data class SelectInvoice(val index: Int) : InvoiceTransactionsAction()
    data class SelectCategory(val category: Category?) : InvoiceTransactionsAction()
    data class SelectType(val type: Transaction.Type?) : InvoiceTransactionsAction()
    data class ToggleRecurring(val enabled: Boolean) : InvoiceTransactionsAction()
    data class ToggleInstallment(val enabled: Boolean) : InvoiceTransactionsAction()
}
