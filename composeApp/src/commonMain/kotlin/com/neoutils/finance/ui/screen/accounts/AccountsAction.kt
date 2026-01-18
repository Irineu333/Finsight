package com.neoutils.finance.ui.screen.accounts

import com.neoutils.finance.domain.model.Category
import com.neoutils.finance.domain.model.Transaction

sealed class AccountsAction {
    data class SelectAccount(val index: Int) : AccountsAction()
    data class SelectCategory(val category: Category?) : AccountsAction()
    data class SelectType(val type: Transaction.Type?) : AccountsAction()
    data object PreviousMonth : AccountsAction()
    data object NextMonth : AccountsAction()
}
