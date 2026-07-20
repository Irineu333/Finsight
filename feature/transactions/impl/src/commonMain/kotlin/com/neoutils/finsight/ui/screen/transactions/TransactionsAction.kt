package com.neoutils.finsight.ui.screen.transactions

import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.TransactionTarget
import com.neoutils.finsight.domain.model.TransactionType
import kotlinx.datetime.YearMonth

sealed class TransactionsAction {

    data object PreviousMonth : TransactionsAction()
    data object NextMonth : TransactionsAction()
    data class SelectMonth(val yearMonth: YearMonth) : TransactionsAction()

    data class SelectCategory(val category: Category?) : TransactionsAction()
    data class SelectType(val type: TransactionType?) : TransactionsAction()
    data class SelectTarget(val target: TransactionTarget?) : TransactionsAction()
    data class ToggleRecurring(val enabled: Boolean) : TransactionsAction()
    data class ToggleInstallment(val enabled: Boolean) : TransactionsAction()
}
