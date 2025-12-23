@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.ui.screen.dashboard

import com.neoutils.finance.domain.model.CategorySpending
import com.neoutils.finance.domain.model.CreditCard
import com.neoutils.finance.domain.model.Invoice
import com.neoutils.finance.domain.model.Transaction
import com.neoutils.finance.extension.toYearMonth
import com.neoutils.finance.ui.model.InvoiceUi
import kotlinx.datetime.YearMonth
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

data class DashboardUiState(
    val recents: List<Transaction> = emptyList(),
    val balance: BalanceStats = BalanceStats(),
    val yearMonth: YearMonth = Clock.System.now().toYearMonth(),
    val categorySpending: List<CategorySpending> = emptyList(),
    val creditCards: List<CreditCardUi> = emptyList()
) {
    data class BalanceStats(
        val income: Double = 0.0,
        val expense: Double = 0.0,
        val balance: Double = 0.0
    )
}

data class CreditCardUi(
    val creditCard: CreditCard,
    val invoiceUi: InvoiceUi?,
)