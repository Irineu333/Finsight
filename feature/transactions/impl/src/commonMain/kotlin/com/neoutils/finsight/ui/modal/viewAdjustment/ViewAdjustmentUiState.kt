package com.neoutils.finsight.ui.modal.viewAdjustment

import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.extension.closedLegBlockingChange
import com.neoutils.finsight.ui.model.TransactionLegTarget
import com.neoutils.finsight.ui.model.TransactionLegUi
import com.neoutils.finsight.ui.model.toTransactionLegs

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
        val isCardTarget = transaction.hasLiabilityLeg
        val title = transaction.title
        val date = transaction.date

        /**
         * The same leg card the transaction detail is composed of, from the same
         * mapper: an adjustment differs only in what the ledger makes it differ in —
         * the verb, which comes from the `EQUITY` override, and the explicit sign,
         * which comes from the operation surface's rule. Neither is restated here.
         *
         * A card adjustment carries its invoice inside the liability card, as any
         * other liability leg does.
         */
        fun legs(onOpen: ((TransactionLegTarget) -> Unit)? = null): List<TransactionLegUi> =
            transaction.toTransactionLegs(invoice = invoice, onOpen = onOpen)

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
