@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.ui.screen.transactions

import com.neoutils.finance.domain.model.Category
import com.neoutils.finance.domain.model.Invoice
import com.neoutils.finance.domain.model.Operation
import com.neoutils.finance.domain.model.Transaction
import com.neoutils.finance.extension.toYearMonth
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth

private val currentMonth
    get() = Clock.System.now().toYearMonth()

data class TransactionsUiState(
    val operations: Map<LocalDate, List<Operation>> = emptyMap(),
    val balanceOverview: BalanceOverview = BalanceOverview(),
    val selectedYearMonth: YearMonth = Clock.System.now().toYearMonth(),
    val selectedCategory: Category? = null,
    val categories: List<Category> = listOf(),
    val selectedType: Transaction.Type? = null,
    val selectedTarget: Transaction.Target? = null
) {

    val isCurrentMonth = selectedYearMonth == currentMonth
    val isFutureMonth = selectedYearMonth > currentMonth

    data class BalanceOverview(
        val initialBalance: Double = 0.0,
        val income: Double = 0.0,
        val expense: Double = 0.0,
        val adjustment: Double = 0.0,
        val finalBalance: Double = 0.0,
        val payment: Double = 0.0,
    ) {
        val mustShowPayment = payment != 0.0
        val mustShowAccountAdjustment = adjustment != 0.0
    }

    data class CreditCardOverview(
        val expense: Double = 0.0,
        val advancePayment: Double = 0.0,
        val total: Double = 0.0,
        val invoices: List<InvoiceOverview> = emptyList(),
    ) {
        val mustShowAdvancePayment = advancePayment != 0.0
    }

    data class InvoiceOverview(
        val invoiceId: Long,
        val creditCardName: String,
        val invoiceStatus: Invoice.Status,
        val expense: Double,
        val advancePayment: Double,
        val adjustment: Double,
        val total: Double
    ) {
        val mustShowAdjustment = adjustment != 0.0
    }
}
