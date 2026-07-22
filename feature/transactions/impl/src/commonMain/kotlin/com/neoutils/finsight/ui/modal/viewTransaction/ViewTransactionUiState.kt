package com.neoutils.finsight.ui.modal.viewTransaction

import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionInstallment
import com.neoutils.finsight.domain.model.TransactionRecurring
import com.neoutils.finsight.domain.model.TransactionLabel
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.extension.closedLegBlockingChange
import com.neoutils.finsight.extension.deriveTransactionType
import com.neoutils.finsight.ui.model.TransactionPerspective
import kotlin.math.abs

sealed interface ViewTransactionUiState {

    data object Loading : ViewTransactionUiState

    data object Error : ViewTransactionUiState

    /**
     * The transaction plus the facades this screen renders around it.
     *
     * They are passed in, not read off the transaction: the ledger carries account
     * ids and dimensions, and turning those into a card, an invoice or a category is
     * the owning feature's job (design D6). Hydrating them here — in the view model —
     * is what keeps the ledger unable to name any of them.
     */
    data class Content(
        val transaction: Transaction,
        val perspective: TransactionPerspective? = null,
        val category: Category? = null,
        val creditCard: CreditCard? = null,
        val invoice: Invoice? = null,
        val installment: TransactionInstallment? = null,
        val recurring: TransactionRecurring? = null,
    ) : ViewTransactionUiState {

        // The entry seen through the current perspective (the account's leg, else
        // the transaction's own money-out leg), used to derive the leg direction.
        private val perspectiveEntry = perspective?.let { selectedPerspective ->
            transaction.entries.firstOrNull { it.account.id == selectedPerspective.accountId }
        } ?: transaction.primaryEntry

        /** Axis 2 — the transaction's nature (title/colour), derived from the entries. */
        val label: TransactionLabel = transaction.label

        /** Axis 1 — the leg's direction under the perspective (the type text). */
        val direction: TransactionType = perspectiveEntry
            ?.let { deriveTransactionType(it.amount, transaction.entries) }
            ?: TransactionType.EXPENSE

        /**
         * The title falls back to the category's name, which is why it is derived
         * here and not on the transaction: only this state has the name.
         */
        val displayTitle: String = transaction.title?.takeIf { it.isNotBlank() }
            ?: category?.name?.takeIf { it.isNotBlank() }
            ?: "Untitled"

        val date = transaction.date
        val account = transaction.sourceAccount
        val isCardTarget = transaction.hasLiabilityLeg
        val amount = abs(perspectiveEntry?.amount ?: 0L) / 100.0

        private val assetEntries = transaction.entries.filter { it.account.type == AccountType.ASSET }

        /** A transfer's two sides: money leaves one asset account and enters another. */
        val sourceAccount = assetEntries.firstOrNull { it.amount < 0 }?.account
        val destinationAccount = assetEntries.firstOrNull { it.amount > 0 }?.account

        /**
         * Whether the ledger lets this transaction be touched at all.
         *
         * A transaction on an archived account or card is frozen: both editing and
         * deleting move movement off it, and it has no balance to spare. Editing is
         * the sharper of the two — retargeting an old transaction changes an
         * archived account's balance without ever writing to it.
         *
         * A category leg does not freeze anything: it is not monetary.
         * The rule is the ledger's ([closedLegBlockingChange]); the screen only
         * decides whether to offer the action.
         *
         * Declared before the two gates below: property initializers run in
         * declaration order, so reading it from above would read `false`.
         */
        val isChangeable: Boolean = transaction.entries.closedLegBlockingChange() == null

        /**
         * Derived edit gate, gate by gate (design D2): not an adjustment, exactly
         * one monetary leg, no installment, and not frozen. The invoice-status gate
         * (CLOSED/PAID blocks edit *and* delete) is applied one level up.
         */
        val isEditable: Boolean =
            label != TransactionLabel.ADJUSTMENT &&
                transaction.monetaryEntries.size == 1 &&
                transaction.installmentId == null &&
                isChangeable

        val isRemovable: Boolean = isChangeable
    }
}
