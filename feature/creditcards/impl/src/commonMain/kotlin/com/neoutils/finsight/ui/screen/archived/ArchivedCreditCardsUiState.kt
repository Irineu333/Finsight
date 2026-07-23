package com.neoutils.finsight.ui.screen.archived

import com.neoutils.finsight.ui.model.ArchivedCreditCardUi

sealed interface ArchivedCreditCardsUiState {

    data object Loading : ArchivedCreditCardsUiState

    data object Empty : ArchivedCreditCardsUiState

    data class Content(
        val creditCards: List<ArchivedCreditCardUi>,
    ) : ArchivedCreditCardsUiState
}
