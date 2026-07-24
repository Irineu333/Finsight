package com.neoutils.finsight.ui.modal.viewCreditCard

import com.neoutils.finsight.ui.model.ArchivedCreditCardUi

sealed interface ViewCreditCardUiState {

    data object Loading : ViewCreditCardUiState

    data object Error : ViewCreditCardUiState

    data class Content(
        val card: ArchivedCreditCardUi,
        val isArchived: Boolean,
        // How many invoices the card has. The balance would always read zero for an
        // archived card (design D8), so this is the informative figure to show instead.
        val invoiceCount: Int,
    ) : ViewCreditCardUiState
}
