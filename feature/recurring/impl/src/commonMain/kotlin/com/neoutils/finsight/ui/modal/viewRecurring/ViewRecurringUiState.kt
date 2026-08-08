package com.neoutils.finsight.ui.modal.viewRecurring

import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.extension.DisplayAmount
import com.neoutils.finsight.ui.model.RetireAction

sealed interface ViewRecurringUiState {

    data object Loading : ViewRecurringUiState

    data object Error : ViewRecurringUiState

    data class Content(
        val recurring: Recurring,
        /**
         * The template's amount, denominated by the account or card it names (design
         * D17) — resolved by the view model, since a card names its account and only
         * the account states a currency.
         *
         * `null` when that account has gone missing: the detail drops the amount row
         * rather than showing the figure under a currency nobody chose for it.
         */
        val amount: DisplayAmount?,
        // Which retire action this screen may offer — resolved by the same owner the
        // delete use case consumes, so the screen never offers a delete it refuses.
        val retireAction: RetireAction,
    ) : ViewRecurringUiState
}
