@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.ui.screen.invoiceTransactions

import com.neoutils.finance.domain.model.Category
import com.neoutils.finance.domain.model.Invoice
import com.neoutils.finance.domain.model.Transaction
import com.neoutils.finance.util.UiText
import kotlin.time.ExperimentalTime
import kotlinx.datetime.LocalDate

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
        val nextDateLabel: UiText?,
        val closingDate: LocalDate,
        val isClosable: Boolean,
    ) {
        val invoiceId = invoice.id
        val status = invoice.status
        val mustShowAdjustment = adjustment != 0.0
        val canEdit = status != Invoice.Status.PAID
    }
}
