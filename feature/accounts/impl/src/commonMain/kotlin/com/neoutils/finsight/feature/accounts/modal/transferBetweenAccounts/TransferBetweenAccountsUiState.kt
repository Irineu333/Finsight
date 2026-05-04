package com.neoutils.finsight.feature.accounts.modal.transferBetweenAccounts

import com.neoutils.finsight.feature.accounts.model.Account

data class TransferBetweenAccountsUiState(
    val accounts: List<Account> = emptyList(),
    val destinationAccounts: List<Account> = emptyList(),
    val selectedSourceAccount: Account? = null,
    val selectedDestinationAccount: Account? = null,
)
