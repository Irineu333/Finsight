@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.ui.screen.transactions

import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.ui.model.InvoiceOverview
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionTarget
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.extension.toYearMonth
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth

private val currentMonth
    get() = Clock.System.now().toYearMonth()

data class TransactionsUiState(
    val transactions: Map<LocalDate, List<Transaction>> = emptyMap(),
    val balanceOverview: BalanceOverview = BalanceOverview(),
    val selectedYearMonth: YearMonth = Clock.System.now().toYearMonth(),
    val selectedCategory: Category? = null,
    val categories: List<Category> = listOf(),
    val selectedType: TransactionType? = null,
    val selectedTarget: TransactionTarget? = null,
    val showRecurringOnly: Boolean = false,
    val showInstallmentOnly: Boolean = false,
) {

    val isCurrentMonth = selectedYearMonth == currentMonth
    val isFutureMonth = selectedYearMonth > currentMonth

    data class BalanceOverview(
        val openingBalance: Double = 0.0,
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
        val total: Double = 0.0,
        val invoices: List<InvoiceOverview> = emptyList(),
    )

}
