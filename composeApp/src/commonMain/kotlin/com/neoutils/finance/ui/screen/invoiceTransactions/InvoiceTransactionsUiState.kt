@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.ui.screen.invoiceTransactions

import com.neoutils.finance.domain.model.Category
import com.neoutils.finance.domain.model.Invoice
import com.neoutils.finance.domain.model.Transaction
import com.neoutils.finance.extension.toYearMonth
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth

data class InvoiceTransactionsUiState(
    val creditCardName: String = "",
    val invoices: List<InvoiceSummary> = emptyList(),
    val selectedInvoiceIndex: Int = 0,
    val transactions: Map<LocalDate, List<Transaction>> = emptyMap(),
    val categories: List<Category> = emptyList(),
    val selectedCategory: Category? = null,
    val selectedType: Transaction.Type? = null,
) {
    data class InvoiceSummary(
        val invoice: Invoice,
        val expense: Double,
        val advancePayment: Double,
        val adjustment: Double,
        val total: Double,
        val dueMonthLabel: String,
    ) {
        private val currentMonth get() = Clock.System.now().toYearMonth()
        
        val invoiceId = invoice.id
        val status = invoice.status
        val mustShowAdjustment = adjustment != 0.0
        val canEdit = status != Invoice.Status.PAID
        val isClosable = status.isOpen && currentMonth >= invoice.closingMonth
    }
}
