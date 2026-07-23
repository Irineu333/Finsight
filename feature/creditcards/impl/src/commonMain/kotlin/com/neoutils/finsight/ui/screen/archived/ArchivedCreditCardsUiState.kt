package com.neoutils.finsight.ui.screen.archived

import com.neoutils.finsight.domain.model.CreditCard

sealed interface ArchivedCreditCardsUiState {

    data object Loading : ArchivedCreditCardsUiState

    data object Empty : ArchivedCreditCardsUiState

    // Rendered from the domain CreditCard: CreditCardUi is built from active cards
    // only and carries no isArchived (design D5).
    data class Content(
        val creditCards: List<CreditCard>,
    ) : ArchivedCreditCardsUiState
}
