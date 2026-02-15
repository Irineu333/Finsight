package com.neoutils.finance.ui.modal.transferBetweenAccounts

import com.neoutils.finance.domain.model.Account

data class TransferBetweenAccountsUiState(
    val accounts: List<Account> = emptyList(),
    val destinationAccounts: List<Account> = emptyList(),
    val selectedSourceAccount: Account? = null,
    val selectedDestinationAccount: Account? = null,
)
