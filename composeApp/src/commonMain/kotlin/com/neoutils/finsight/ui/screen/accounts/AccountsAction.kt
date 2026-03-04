package com.neoutils.finsight.ui.screen.accounts

import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.Transaction
import kotlinx.datetime.YearMonth

sealed class AccountsAction {
    data class SelectAccount(val index: Int) : AccountsAction()
    data class SelectCategory(val category: Category?) : AccountsAction()
    data class SelectType(val type: Transaction.Type?) : AccountsAction()
    data class ToggleRecurring(val enabled: Boolean) : AccountsAction()
    data class SelectMonth(val yearMonth: YearMonth) : AccountsAction()
    data object PreviousMonth : AccountsAction()
    data object NextMonth : AccountsAction()
}
