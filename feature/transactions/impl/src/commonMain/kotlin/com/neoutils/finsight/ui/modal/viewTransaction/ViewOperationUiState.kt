package com.neoutils.finsight.ui.modal.viewTransaction

import com.neoutils.finsight.domain.model.Operation
import com.neoutils.finsight.domain.model.OperationLabel
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.extension.deriveOperationLabel
import com.neoutils.finsight.extension.deriveTransactionType
import com.neoutils.finsight.ui.model.TransactionPerspective

sealed interface ViewOperationUiState {

    data object Loading : ViewOperationUiState

    data object Error : ViewOperationUiState

    data class Content(
        val operation: Operation,
        val perspective: TransactionPerspective? = null,
    ) : ViewOperationUiState {
        val transaction = perspective?.let { selectedPerspective ->
            selectedPerspective.resolve(operation = operation)
        } ?: operation.primaryTransaction

        // The entry seen through the current perspective (the account's leg, else
        // the operation's own money-out leg), used to derive the leg direction.
        private val perspectiveEntry = perspective?.let { selectedPerspective ->
            operation.entries.firstOrNull { it.account.id == selectedPerspective.accountId }
        } ?: operation.entries.filter { it.account.type.isMonetary }.minByOrNull { it.amount }

        /** Axis 2 — the operation's nature (title/colour), derived from the entries. */
        val label: OperationLabel = operation.entries.deriveOperationLabel()

        /** Axis 1 — the leg's direction under the perspective (the type text). */
        val direction: Transaction.Type = perspectiveEntry
            ?.let { deriveTransactionType(it.amount, operation.entries) }
            ?: transaction.type

        /**
         * Derived edit gate, gate by gate (design D2): not an adjustment, exactly
         * one monetary leg, no installment, and — until the legacy leg is gone
         * (§6.9) — not a card leg whose facade was deleted. The invoice-status gate
         * (CLOSED/PAID blocks edit *and* delete) is applied one level up.
         */
        val isEditable: Boolean =
            label != OperationLabel.ADJUSTMENT &&
                operation.entries.count { it.account.type.isMonetary } == 1 &&
                operation.installment == null &&
                !(transaction.target == Transaction.Target.CREDIT_CARD && transaction.creditCard == null)
    }
}
