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
         */
        val isDeletable = invoice?.status?.isEditable ?: true
    }
}
