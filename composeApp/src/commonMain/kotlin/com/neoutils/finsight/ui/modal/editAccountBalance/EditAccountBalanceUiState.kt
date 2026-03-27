package com.neoutils.finsight.ui.modal.editAccountBalance

import com.neoutils.finsight.domain.model.Account

sealed interface EditAccountBalanceUiState {
    data object Loading : EditAccountBalanceUiState
    data class Content(
        val accounts: List<Account>,
        val selectedAccount: Account,
        val currentBalance: Double,
    ) : EditAccountBalanceUiState
}