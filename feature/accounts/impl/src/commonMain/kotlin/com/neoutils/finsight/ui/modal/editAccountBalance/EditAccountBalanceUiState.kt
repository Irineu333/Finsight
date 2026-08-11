package com.neoutils.finsight.ui.modal.editAccountBalance

import com.neoutils.finsight.domain.model.Account
import kotlinx.datetime.LocalDate

sealed interface EditAccountBalanceUiState {
    data object Loading : EditAccountBalanceUiState

    /**
     * [currentBalance] is the balance **on [date]** — the single clock of the form. The
     * value shown and the value the difference is applied to are the same one, which is
     * what keeps the displayed difference equal to the written one.
     */
    data class Content(
        val accounts: List<Account>,
        val selectedAccount: Account,
        val currentBalance: Double,
        val date: String,
        val today: LocalDate,
    ) : EditAccountBalanceUiState
}
