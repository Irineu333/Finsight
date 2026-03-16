package com.neoutils.finsight.ui.screen.home

import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.ui.screen.report.config.PerspectiveTab
import kotlinx.serialization.Serializable

sealed class HomeRoute {
    @Serializable
    data object Dashboard : HomeRoute()

    @Serializable
    data class Transactions(
        val filterType: Transaction.Type? = null,
        val filterTarget: Transaction.Target? = null
    ) : HomeRoute()
}

sealed class AppRoute {

    @Serializable
    data object Home : AppRoute()

    @Serializable
    data object Categories : AppRoute()

    @Serializable
    data class CreditCards(val creditCardId: Long? = null) : AppRoute()

    @Serializable
    data class InvoiceTransactions(val creditCardId: Long) : AppRoute()

    @Serializable
    data class Accounts(val accountId: Long? = null) : AppRoute()

    @Serializable
    data object Installments : AppRoute()

    @Serializable
    data object Budgets : AppRoute()

    @Serializable
    data object Recurring : AppRoute()

    @Serializable
    data object ReportConfig : AppRoute()

    @Serializable
    data class ReportViewer(
        val perspectiveType: PerspectiveTab,
        val accountIds: List<Long> = emptyList(),
        val creditCardId: Long? = null,
        val invoiceIds: List<Long> = emptyList(),
        val startDate: String,
        val endDate: String,
        val includeSpendingByCategory: Boolean,
        val includeIncomeByCategory: Boolean = true,
        val includeTransactionList: Boolean,
    ) : AppRoute()
}
