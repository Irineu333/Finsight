package com.neoutils.finsight.ui.modal.viewCreditCard

import com.neoutils.finsight.domain.model.CreditCard

sealed interface ViewCreditCardUiState {

    data object Loading : ViewCreditCardUiState

    data object Error : ViewCreditCardUiState

    data class Content(
        val creditCard: CreditCard,
        // How many invoices the card has. The balance would always read zero for an
        // archived card (design D8), so this is the informative figure to show instead.
        val invoiceCount: Int,
    ) : ViewCreditCardUiState
}
