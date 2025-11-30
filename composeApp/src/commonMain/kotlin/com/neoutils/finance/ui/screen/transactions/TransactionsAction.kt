package com.neoutils.finance.ui.screen.transactions

sealed class TransactionsAction {
    data class AdjustInitialBalance(
        val target: Double
    ) : TransactionsAction()

    data class AdjustBalance(
        val target: Double
    ) : TransactionsAction()

    data object PreviousMonth : TransactionsAction()
    data object NextMonth : TransactionsAction()
}