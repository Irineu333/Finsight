package com.neoutils.finsight.ui.screen.transactions

import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.TransactionLabel
import com.neoutils.finsight.domain.model.TransactionTarget
import kotlinx.datetime.YearMonth

sealed class TransactionsAction {

    data class SelectMonth(val yearMonth: YearMonth) : TransactionsAction()

    data class SelectScope(val scope: TransactionScope) : TransactionsAction()

    data class SelectCategory(val category: Category?) : TransactionsAction()
    data class SelectLabel(val label: TransactionLabel?) : TransactionsAction()
    data class SelectTarget(val target: TransactionTarget?) : TransactionsAction()
    data class ToggleRecurring(val enabled: Boolean) : TransactionsAction()
    data class ToggleInstallment(val enabled: Boolean) : TransactionsAction()
}
