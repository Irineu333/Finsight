package com.neoutils.finance.ui.screen.transactions

import com.neoutils.finance.domain.model.Category
import com.neoutils.finance.domain.model.Transaction

sealed class TransactionsAction {

    data object PreviousMonth : TransactionsAction()
    data object NextMonth : TransactionsAction()

    data class SelectCategory(val category: Category?) : TransactionsAction()
    data class SelectType(val type: Transaction.Type?) : TransactionsAction()
    data class SelectTarget(val target: Transaction.Target?) : TransactionsAction()
}