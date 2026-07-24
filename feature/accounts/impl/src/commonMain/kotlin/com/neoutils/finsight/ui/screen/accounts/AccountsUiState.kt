package com.neoutils.finsight.ui.screen.accounts

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.ui.model.AccountUi
import com.neoutils.finsight.ui.model.TransactionUi
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth

sealed class AccountsUiState {
    abstract val selectedMonth: YearMonth

    data class Loading(
        override val selectedMonth: YearMonth,
    ) : AccountsUiState()

    data class Content(
        val accounts: List<AccountUi>,
        // The domain accounts paired positionally with [accounts]; the screen
        // resolves the Account for a card or a modal action from here, keeping
        // the display model (AccountUi) free of domain (presentation-mapping).
        val domainAccounts: List<Account> = emptyList(),
        val selectedAccountIndex: Int,
        val selectedAccountId: Long? = null,
        val transactions: Map<LocalDate, List<TransactionUi>>,
        val categories: List<Category>,
        val selectedCategory: Category? = null,
        val selectedType: TransactionType? = null,
        val showRecurringOnly: Boolean = false,
        override val selectedMonth: YearMonth,
    ) : AccountsUiState()
}
