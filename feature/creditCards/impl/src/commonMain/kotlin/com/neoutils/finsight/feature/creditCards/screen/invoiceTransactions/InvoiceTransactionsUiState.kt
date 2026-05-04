@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.feature.creditCards.screen.invoiceTransactions

import com.neoutils.finsight.feature.categories.model.Category
import com.neoutils.finsight.feature.creditCards.model.CreditCard
import com.neoutils.finsight.feature.creditCards.model.Invoice
import com.neoutils.finsight.feature.transactions.model.Operation
import com.neoutils.finsight.feature.transactions.model.Transaction
import com.neoutils.finsight.core.ui.util.UiText
import kotlin.time.ExperimentalTime
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth

data class InvoiceTransactionsUiState(
    val creditCardName: String = "",
    val creditCard: CreditCard? = null,
    val invoices: List<InvoiceSummary> = emptyList(),
    val selectedInvoiceIndex: Int = 0,
    val operations: Map<LocalDate, List<Operation>> = emptyMap(),
    val categories: List<Category> = emptyList(),
    val selectedCategory: Category? = null,
    val selectedType: Transaction.Type? = null,
    val showRecurringOnly: Boolean = false,
    val showInstallmentOnly: Boolean = false,
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
