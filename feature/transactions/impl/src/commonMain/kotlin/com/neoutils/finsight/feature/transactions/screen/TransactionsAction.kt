package com.neoutils.finsight.feature.transactions.screen

import com.neoutils.finsight.core.domain.model.Category
import com.neoutils.finsight.feature.transactions.model.Transaction
import kotlinx.datetime.YearMonth

sealed class TransactionsAction {

    data object PreviousMonth : TransactionsAction()
    data object NextMonth : TransactionsAction()
    data class SelectMonth(val yearMonth: YearMonth) : TransactionsAction()

    data class SelectCategory(val category: Category?) : TransactionsAction()
    data class SelectType(val type: Transaction.Type?) : TransactionsAction()
    data class SelectTarget(val target: Transaction.Target?) : TransactionsAction()
    data class ToggleRecurring(val enabled: Boolean) : TransactionsAction()
    data class ToggleInstallment(val enabled: Boolean) : TransactionsAction()
}
