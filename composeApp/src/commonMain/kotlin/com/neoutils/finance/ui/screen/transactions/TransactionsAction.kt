package com.neoutils.finance.ui.screen.transactions

import com.neoutils.finance.data.TransactionEntry

sealed class TransactionsAction {
    data class AdjustInitialBalance(
        val target: Double
    ) : TransactionsAction()

    data class AdjustBalance(
        val target: Double
    ) : TransactionsAction()

    data object PreviousMonth : TransactionsAction()
    data object NextMonth : TransactionsAction()

    data class SelectCategory(val categoryId: Long?) : TransactionsAction()
    data class SelectType(val type: TransactionEntry.Type?) : TransactionsAction()
}