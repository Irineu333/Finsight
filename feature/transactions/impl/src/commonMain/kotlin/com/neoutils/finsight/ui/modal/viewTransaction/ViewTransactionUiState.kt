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
import com.neoutils.finsight.domain.usecase.impliedRate
import com.neoutils.finsight.extension.DisplayAmount
import com.neoutils.finsight.extension.closedLegBlockingChange
import com.neoutils.finsight.extension.displayTitleOf
import com.neoutils.finsight.extension.liabilityLeg
import com.neoutils.finsight.extension.deriveTransactionType
import com.neoutils.finsight.ui.model.TransactionPerspective
import com.neoutils.finsight.ui.model.itemDisplayAmount
import com.neoutils.finsight.ui.model.legUnder
import kotlin.math.abs

sealed interface ViewTransactionUiState {

    data object Loading : ViewTransactionUiState

    data object Error : ViewTransactionUiState

    /**
     * The rate an operation applied, read off its own two ends.
     *
     * It is a quotient, never a field: no leg, intent or contra carries a rate anywhere
     * on the write path (design D6), so the detail derives it exactly as the write form
     * derived it while the user was typing — the same [impliedRate], in the same
     * direction, so the number read afterwards is the number that was shown.
     */
    data class AppliedRate(
        val sourceCurrency: String,
        val targetCurrency: String,
        /** Units of [targetCurrency] per one unit of [sourceCurrency]. */
        val rate: Double,
    )

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

        // The entry seen through the current perspective, from the one definition of it,
        // so the detail cannot read a different leg than the list it was opened from.
        //
        // A list drops an item whose perspective has no leg; a detail has nothing to drop
        // to, so it falls back to the transaction's own leg rather than render nothing.
        private val perspectiveEntry = transaction.legUnder(perspective?.accountId)
            ?: transaction.primaryEntry

        /** Axis 2 — the transaction's nature (title/colour), derived from the entries. */
        val label: TransactionLabel = transaction.label

        /** Axis 1 — the leg's direction under the perspective (the type text). */
        val direction: TransactionType = perspectiveEntry
            ?.let { deriveTransactionType(it.amount, transaction.entries) }
            ?: TransactionType.EXPENSE

        val displayTitle: String = displayTitleOf(transaction.title, category)

        val date = transaction.date
        val account = transaction.sourceAccount
        val isCardTarget = transaction.hasLiabilityLeg
        // The same item-surface rule the list reads through, not a second copy of it:
        // a detail that disagreed with the card it was opened from would be a defect.
        //
        // Denominated by the leg's **own** account, never the base: the line of a
        // statement is a single entry and reads in the currency it was recorded in
        // (design D29). With no leg there is no currency either, so there is no amount
        // to state — a transaction the ledger cannot produce.
        val amount: DisplayAmount? = perspectiveEntry?.let { entry ->
            itemDisplayAmount(
                label = label,
                legAmountCents = entry.amount,
                currency = entry.currency,
                hasPerspective = perspective != null,
            )
        }

        /**
         * The instalment's total, denominated by the **card** it sits on — read off the
         * liability leg's account, which is the one account an instalment names (design
         * D17). Absent when this transaction is no instalment, or carries no card leg to
         * take the currency from.
         */
        val installmentTotal: DisplayAmount? = installment?.let { arrangement ->
            transaction.entries.liabilityLeg()?.let { leg ->
                DisplayAmount.magnitude(
                    value = arrangement.instance.totalAmount,
                    currency = leg.currency,
                    isApproximate = false,
                )
            }
        }

        /**
         * The rate this operation applied, when it crossed currencies — and `null`
         * whenever it did not, which is every single-currency operation and every one
         * with a single monetary leg (a card purchase has nothing to divide by).
         *
         * The two ends are the ledger's own: the leg money left ([Transaction.primaryEntry],
         * the one owner of "outgoing") and the monetary leg it entered. The conversion legs
         * take no part — they are not monetary, and they hold the rounding residue, not the
         * rate. The direction is the write form's, source → target ([impliedRate]).
         */
        val appliedRate: AppliedRate? = run {
            val out = transaction.primaryEntry?.takeIf { it.amount < 0 } ?: return@run null
            val into = transaction.monetaryEntries.firstOrNull { it.amount > 0 } ?: return@run null
            if (out.currency == into.currency) return@run null

            impliedRate(
                sourceAmount = abs(out.amount) / 100.0,
                targetAmount = abs(into.amount) / 100.0,
            )?.let { rate ->
                AppliedRate(
                    sourceCurrency = out.currency,
                    targetCurrency = into.currency,
                    rate = rate,
                )
            }
        }

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
