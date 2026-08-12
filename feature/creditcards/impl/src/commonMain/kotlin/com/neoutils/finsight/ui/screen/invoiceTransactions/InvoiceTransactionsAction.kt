package com.neoutils.finsight.ui.screen.invoiceTransactions

import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.TransactionType
import kotlinx.datetime.YearMonth

sealed class InvoiceTransactionsAction {
    data class SelectInvoice(val index: Int) : InvoiceTransactionsAction()

    /**
     * Points the pager at the invoice due on [dueMonth], wherever the calendar order put
     * it. The month is what the caller knows after creating one; the index is not.
     */
    data class SelectInvoiceForDueMonth(val dueMonth: YearMonth) : InvoiceTransactionsAction()
    data class SelectCategory(val category: Category?) : InvoiceTransactionsAction()
    data class SelectType(val type: TransactionType?) : InvoiceTransactionsAction()
    data class ToggleRecurring(val enabled: Boolean) : InvoiceTransactionsAction()
    data class ToggleInstallment(val enabled: Boolean) : InvoiceTransactionsAction()

    /** Returns the list filters to neutral. The selected invoice is not a filter. */
    data object ClearFilters : InvoiceTransactionsAction()

    data object Unarchive : InvoiceTransactionsAction()
}
