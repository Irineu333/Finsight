package com.neoutils.finsight.ui.screen.accounts

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.SpendingSubject
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.ui.model.AccountUi
import com.neoutils.finsight.ui.model.TransactionUi
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth

sealed class AccountsUiState {
    abstract val selectedMonth: YearMonth

    /** The clock the screen reasons with — where a balance adjustment's ceiling comes from. */
    abstract val today: LocalDate

    data class Loading(
        override val selectedMonth: YearMonth,
        override val today: LocalDate,
    ) : AccountsUiState()

    data class Content(
        val accounts: List<AccountUi>,
        // The domain accounts paired positionally with [accounts]; the screen
        // resolves the Account for a card or a modal action from here, keeping
        // the display model (AccountUi) free of domain (presentation-mapping).
        val domainAccounts: List<Account> = emptyList(),
        val selectedAccountIndex: Int,
        val selectedAccountId: Long? = null,
        val listState: ListState,
        val categories: List<Category>,
        val selectedSubject: SpendingSubject? = null,
        val selectedType: TransactionType? = null,
        val showRecurringOnly: Boolean = false,
        override val selectedMonth: YearMonth,
        override val today: LocalDate,
    ) : AccountsUiState()

    /**
     * What stands where the list goes. The transactions live *inside* [ListState.Content]
     * rather than beside it, so there is no empty map awaiting interpretation: a state
     * with no list is one of the two emptinesses, by construction and not by convention.
     *
     * There is no loading case here — [AccountsUiState] already has one, and [Content]
     * only exists after the first read.
     *
     * The chrome above the list — the account pager, its actions and the chips — is not
     * part of this: it survives every state, since it is the only way out of an empty one.
     */
    sealed interface ListState {

        /** The selected account has no transaction at all, in any month. */
        data object EmptyAccount : ListState

        /**
         * The account has transactions; none survives the current cut — the month or the
         * filters. [canClearFilters] is false when every list filter is already neutral,
         * as in a month with no entries: offering to clear then would promise a change
         * the button cannot deliver.
         */
        data class EmptyScope(val canClearFilters: Boolean) : ListState

        data class Content(
            val transactions: Map<LocalDate, List<TransactionUi>>,
        ) : ListState
    }
}
