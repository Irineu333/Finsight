package com.neoutils.finsight.ui.modal.viewAccount

import com.neoutils.finsight.ui.model.ArchivedAccountUi

sealed interface ViewAccountUiState {

    data object Loading : ViewAccountUiState

    data object Error : ViewAccountUiState

    data class Content(
        val account: ArchivedAccountUi,
    ) : ViewAccountUiState
}
