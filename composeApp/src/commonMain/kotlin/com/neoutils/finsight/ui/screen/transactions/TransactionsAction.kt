package com.neoutils.finsight.ui.screen.transactions

import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.model.Transaction
import kotlinx.datetime.YearMonth

sealed class TransactionsAction {

    data object PreviousMonth : TransactionsAction()
    data object NextMonth : TransactionsAction()
    data class SelectMonth(val yearMonth: YearMonth) : TransactionsAction()

    data class SelectCategory(val category: Category?) : TransactionsAction()
    data class SelectType(val type: Transaction.Type?) : TransactionsAction()
    data class SelectTarget(val target: Transaction.Target?) : TransactionsAction()
    data class SelectRecurring(val recurring: Recurring?) : TransactionsAction()
}
