package com.neoutils.finsight.ui.modal.viewTransaction

import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Operation
import com.neoutils.finsight.domain.model.OperationLabel
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.extension.deriveTransactionType
import com.neoutils.finsight.ui.model.TransactionPerspective
import kotlin.math.abs

sealed interface ViewOperationUiState {

    data object Loading : ViewOperationUiState

    data object Error : ViewOperationUiState

    data class Content(
        val operation: Operation,
        val perspective: TransactionPerspective? = null,
    ) : ViewOperationUiState {

        // The entry seen through the current perspective (the account's leg, else
        // the operation's own money-out leg), used to derive the leg direction.
        private val perspectiveEntry = perspective?.let { selectedPerspective ->
            operation.entries.firstOrNull { it.account.id == selectedPerspective.accountId }
        } ?: operation.primaryEntry

        /** Axis 2 — the operation's nature (title/colour), derived from the entries. */
        val label: OperationLabel = operation.label

        /** Axis 1 — the leg's direction under the perspective (the type text). */
        val direction: TransactionType = perspectiveEntry
            ?.let { deriveTransactionType(it.amount, operation.entries) }
            ?: TransactionType.EXPENSE

        val category = operation.category
        val date = operation.date
        val account = operation.sourceAccount
        val creditCard = operation.targetCreditCard
        val invoice = operation.targetInvoice
        val isCardTarget = operation.isCardTarget
        val amount = abs(perspectiveEntry?.amount ?: 0L) / 100.0

        private val assetEntries = operation.entries.filter { it.account.type == AccountType.ASSET }

        /** A transfer's two sides: money leaves one asset account and enters another. */
        val sourceAccount = assetEntries.firstOrNull { it.amount < 0 }?.account
        val destinationAccount = assetEntries.firstOrNull { it.amount > 0 }?.account

        /**
         * Derived edit gate, gate by gate (design D2): not an adjustment, exactly
         * one monetary leg, and no installment. The invoice-status gate
         * (CLOSED/PAID blocks edit *and* delete) is applied one level up.
         */
        val isEditable: Boolean =
            label != OperationLabel.ADJUSTMENT &&
                operation.monetaryEntries.size == 1 &&
                operation.installment == null
    }
}
