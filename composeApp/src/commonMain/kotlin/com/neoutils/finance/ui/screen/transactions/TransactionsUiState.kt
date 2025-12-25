@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.ui.screen.transactions

import com.neoutils.finance.domain.model.Category
import com.neoutils.finance.domain.model.Transaction
import com.neoutils.finance.extension.toYearMonth
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.yearMonth

private val currentMonth
    get() = Clock.System.now().toYearMonth()

data class TransactionsUiState(
    val transactions: Map<LocalDate, List<Transaction>> = emptyMap(),
    val balanceOverview: BalanceOverview = BalanceOverview(),
    val creditCardOverview: CreditCardOverview = CreditCardOverview(),
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
        val accountExpense: Double = 0.0,
        val creditCardExpense: Double = 0.0,
        val accountAdjustment: Double = 0.0,
        val creditCardAdjustment: Double = 0.0,
        val invoicePayment: Double = 0.0,
        val finalBalance: Double = 0.0
    ) {
        val mustShowInvoicePayment = invoicePayment != 0.0
        val mustShowAccountAdjustment = accountAdjustment != 0.0
    }

    data class CreditCardOverview(
        val expense: Double = 0.0,
        val invoicePayment: Double = 0.0,
        val advancePayment: Double = 0.0,
        val adjustment: Double = 0.0
    ) {
        val hasData = expense != 0.0 ||
                invoicePayment != 0.0 ||
                advancePayment != 0.0 ||
                adjustment != 0.0

        val mustShowInvoicePayment = invoicePayment != 0.0
        val mustShowAdvancePayment = advancePayment != 0.0
        val mustShowAdjustment = adjustment != 0.0
    }
}
