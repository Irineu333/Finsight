package com.neoutils.finance.ui.screen.home

import com.neoutils.finance.domain.model.Transaction
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
    data object CreditCards : AppRoute()

    @Serializable
    data class InvoiceTransactions(val creditCardId: Long) : AppRoute()
}