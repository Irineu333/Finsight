package com.neoutils.finsight.ui.screen.accounts

import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.Operation
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.ui.model.AccountUi
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth

sealed class AccountsUiState {
    abstract val selectedMonth: YearMonth

    data class Loading(
        override val selectedMonth: YearMonth,
    ) : AccountsUiState()

    data class Content(
        val accounts: List<AccountUi>,
        val selectedAccountIndex: Int,
        val operations: Map<LocalDate, List<Operation>>,
        val categories: List<Category>,
        val selectedCategory: Category? = null,
        val selectedType: Transaction.Type? = null,
        val showRecurringOnly: Boolean = false,
        override val selectedMonth: YearMonth,
    ) : AccountsUiState()
}
