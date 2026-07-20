package com.neoutils.finsight.ui.modal.viewAdjustment

import com.neoutils.finsight.domain.model.Operation

sealed interface ViewAdjustmentUiState {

    data object Loading : ViewAdjustmentUiState

    data object Error : ViewAdjustmentUiState

    data class Content(
        val operation: Operation,
    ) : ViewAdjustmentUiState {
        val isCardTarget = operation.isCardTarget
        val title = operation.title
        val date = operation.date
        val account = operation.sourceAccount
        val creditCard = operation.targetCreditCard
        val invoice = operation.targetInvoice

        /**
         * An adjustment is the one operation whose sign the user must see: it says
         * which way the balance was corrected. Read off the money leg, where the
         * ledger already carries it.
         */
        val signedAmount = (operation.primaryEntry?.amount ?: 0L) / 100.0
    }
}
