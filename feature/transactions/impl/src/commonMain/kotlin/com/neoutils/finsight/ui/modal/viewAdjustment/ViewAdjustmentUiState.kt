package com.neoutils.finsight.ui.modal.viewAdjustment

import com.neoutils.finsight.domain.model.Transaction

sealed interface ViewAdjustmentUiState {

    data object Loading : ViewAdjustmentUiState

    data object Error : ViewAdjustmentUiState

    data class Content(
        val transaction: Transaction,
    ) : ViewAdjustmentUiState {
        val isCardTarget = transaction.isCardTarget
        val title = transaction.title
        val date = transaction.date
        val account = transaction.sourceAccount
        val creditCard = transaction.targetCreditCard
        val invoice = transaction.targetInvoice

        /**
         * An adjustment is the one transaction whose sign the user must see: it says
         * which way the balance was corrected. Read off the money leg, where the
         * ledger already carries it.
         */
        val signedAmount = (transaction.primaryEntry?.amount ?: 0L) / 100.0

        /**
         * A closed or paid invoice is immutable, so the screen stops offering to
         * delete its adjustment. The invariant itself lives at the write boundary;
         * this only keeps the UI from proposing what would be refused.
         *
         * An account adjustment has no invoice and is always deletable; a card one
         * whose invoice did not resolve fails **closed**, so the screen hides the
         * action rather than offering to delete what it could not verify.
         */
        val isDeletable = if (isCardTarget) invoice?.status?.isEditable == true else true
    }
}
