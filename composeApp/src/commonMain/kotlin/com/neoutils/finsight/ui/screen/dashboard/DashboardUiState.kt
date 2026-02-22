@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.ui.screen.dashboard

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.CategorySpending
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.Operation
import com.neoutils.finsight.extension.toYearMonth
import com.neoutils.finsight.ui.model.InvoiceUi
import kotlinx.datetime.YearMonth
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

data class DashboardUiState(
    val accounts: List<DashboardAccountUi> = emptyList(),
    val recents: List<Operation> = emptyList(),
    val hasMoreRecents: Boolean = false,
    val balance: BalanceStats = BalanceStats(),
    val yearMonth: YearMonth = Clock.System.now().toYearMonth(),
    val categorySpending: List<CategorySpending> = emptyList(),
    val creditCards: List<CreditCardUi> = emptyList()
) {
    data class BalanceStats(
        val income: Double = 0.0,
        val expense: Double = 0.0,
        val payment: Double = 0.0,
        val balance: Double = 0.0,
    ) {
        val mustShowPayment = payment != 0.0
    }
}

data class CreditCardUi(
    val creditCard: CreditCard,
    val invoiceUi: InvoiceUi?,
)

data class DashboardAccountUi(
    val account: Account,
    val balance: Double,
)