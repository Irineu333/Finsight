package com.neoutils.finsight.ui.modal.viewCreditCard

import com.neoutils.finsight.domain.model.CreditCard

sealed interface ViewCreditCardUiState {

    data object Loading : ViewCreditCardUiState

    data object Error : ViewCreditCardUiState

    data class Content(
        val creditCard: CreditCard,
        // Σ entries on the card's LIABILITY account, read from the ledger — the card
        // model carries no balance of its own. Zero for an archived card by invariant.
        val balance: Double,
    ) : ViewCreditCardUiState
}
