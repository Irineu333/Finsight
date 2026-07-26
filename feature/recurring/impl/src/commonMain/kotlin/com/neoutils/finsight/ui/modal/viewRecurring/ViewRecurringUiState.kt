package com.neoutils.finsight.ui.modal.viewRecurring

import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.ui.model.RetireAction

sealed interface ViewRecurringUiState {

    data object Loading : ViewRecurringUiState

    data object Error : ViewRecurringUiState

    data class Content(
        val recurring: Recurring,
        // Which retire action this screen may offer — resolved by the same owner the
        // delete use case consumes, so the screen never offers a delete it refuses.
        val retireAction: RetireAction,
    ) : ViewRecurringUiState
}
