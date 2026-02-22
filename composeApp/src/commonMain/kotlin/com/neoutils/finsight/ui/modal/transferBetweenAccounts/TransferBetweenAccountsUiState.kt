package com.neoutils.finsight.ui.modal.transferBetweenAccounts

import com.neoutils.finsight.domain.model.Account

data class TransferBetweenAccountsUiState(
    val accounts: List<Account> = emptyList(),
    val destinationAccounts: List<Account> = emptyList(),
    val selectedSourceAccount: Account? = null,
    val selectedDestinationAccount: Account? = null,
)
