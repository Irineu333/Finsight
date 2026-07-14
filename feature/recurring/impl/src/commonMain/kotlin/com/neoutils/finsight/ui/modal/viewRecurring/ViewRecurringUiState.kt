package com.neoutils.finsight.ui.modal.viewRecurring

import com.neoutils.finsight.domain.model.Recurring

sealed interface ViewRecurringUiState {

    data object Loading : ViewRecurringUiState

    data object Error : ViewRecurringUiState

    data class Content(
        val recurring: Recurring,
    ) : ViewRecurringUiState
}
