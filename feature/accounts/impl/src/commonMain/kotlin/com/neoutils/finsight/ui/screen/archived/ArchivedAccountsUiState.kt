package com.neoutils.finsight.ui.screen.archived

import com.neoutils.finsight.ui.model.ArchivedAccountUi

sealed interface ArchivedAccountsUiState {

    data object Loading : ArchivedAccountsUiState

    data object Empty : ArchivedAccountsUiState

    data class Content(
        val accounts: List<ArchivedAccountUi>,
    ) : ArchivedAccountsUiState
}
