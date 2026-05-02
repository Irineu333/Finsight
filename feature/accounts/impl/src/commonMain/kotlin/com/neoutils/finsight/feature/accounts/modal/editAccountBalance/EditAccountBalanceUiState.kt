package com.neoutils.finsight.feature.accounts.modal.editAccountBalance

import com.neoutils.finsight.core.domain.model.Account

sealed interface EditAccountBalanceUiState {
    data object Loading : EditAccountBalanceUiState
    data class Content(
        val accounts: List<Account>,
        val selectedAccount: Account,
        val currentBalance: Double,
    ) : EditAccountBalanceUiState
}