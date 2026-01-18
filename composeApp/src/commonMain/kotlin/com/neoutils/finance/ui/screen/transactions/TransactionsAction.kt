package com.neoutils.finance.ui.screen.transactions

import com.neoutils.finance.domain.model.Category
import com.neoutils.finance.domain.model.Transaction
import kotlinx.datetime.YearMonth

sealed class TransactionsAction {

    data object PreviousMonth : TransactionsAction()
    data object NextMonth : TransactionsAction()
    data class SelectMonth(val yearMonth: YearMonth) : TransactionsAction()

    data class SelectCategory(val category: Category?) : TransactionsAction()
    data class SelectType(val type: Transaction.Type?) : TransactionsAction()
    data class SelectTarget(val target: Transaction.Target?) : TransactionsAction()
}