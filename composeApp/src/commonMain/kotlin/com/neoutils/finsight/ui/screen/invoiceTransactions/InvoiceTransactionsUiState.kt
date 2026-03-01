@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.ui.screen.invoiceTransactions

import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.Operation
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.util.UiText
import kotlin.time.ExperimentalTime
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth

data class InvoiceTransactionsUiState(
    val creditCardName: String = "",
    val invoices: List<InvoiceSummary> = emptyList(),
    val selectedInvoiceIndex: Int = 0,
    val operations: Map<LocalDate, List<Operation>> = emptyMap(),
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
        val dueMonth: YearMonth,
        val nextDateLabel: UiText?,
        val closingDate: LocalDate,
        val isClosable: Boolean,
    ) {
        val invoiceId = invoice.id
        val status = invoice.status
        val mustShowAdjustment = adjustment != 0.0
        val canEdit = status.isRetroactive || status.isOpen || status.isFuture
    }
}
