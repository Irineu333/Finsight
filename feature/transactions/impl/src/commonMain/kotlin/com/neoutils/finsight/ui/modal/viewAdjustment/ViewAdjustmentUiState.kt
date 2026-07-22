package com.neoutils.finsight.ui.modal.viewAdjustment

import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.extension.closedLegBlockingChange

sealed interface ViewAdjustmentUiState {

    data object Loading : ViewAdjustmentUiState

    data object Error : ViewAdjustmentUiState

    // The card and the invoice are hydrated by the view model from the ledger's
    // identities — the transaction names neither (design D6).
    data class Content(
        val transaction: Transaction,
        val creditCard: CreditCard? = null,
        val invoice: Invoice? = null,
    ) : ViewAdjustmentUiState {
        val isCardTarget = transaction.isCardTarget
        val title = transaction.title
        val date = transaction.date
        val account = transaction.sourceAccount

        /**
         * An adjustment is the one transaction whose sign the user must see: it says
         * which way the balance was corrected. Read off the money leg, where the
         * ledger already carries it.
         */
        val signedAmount = (transaction.primaryEntry?.amount ?: 0L) / 100.0

        /**
         * Whether the ledger lets this adjustment be touched at all: false when it
         * sits on an archived account or card, because deleting it would reopen a
         * balance the archive required to be zero. The screen says so instead of
         * only hiding the action.
         */
        val isChangeable = transaction.entries.closedLegBlockingChange() == null

        /**
         * A closed or paid invoice is immutable, so the screen stops offering to
         * delete its adjustment. The invariant itself lives at the write boundary;
         * this only keeps the UI from proposing what would be refused.
         *
         * An account adjustment has no invoice; a card one whose invoice did not
         * resolve fails **closed**, so the screen hides the action rather than
         * offering to delete what it could not verify.
         *
         * Either way the removal must also not reopen a balance on an archived
         * account — an adjustment is exactly the kind of transaction that zeroed
         * one before it was archived.
         */
        val isDeletable = isChangeable &&
            if (isCardTarget) invoice?.status?.isEditable == true else true
    }
}
