package com.neoutils.finsight.ui.screen.root

import kotlinx.serialization.Serializable

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
    data object Reports : AppRoute()

    @Serializable
    data object Support : AppRoute()

    @Serializable
    data class SupportIssue(val issueId: String) : AppRoute()
}
