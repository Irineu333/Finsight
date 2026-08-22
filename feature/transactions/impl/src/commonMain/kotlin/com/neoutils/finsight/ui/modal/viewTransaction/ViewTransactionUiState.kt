package com.neoutils.finsight.ui.modal.viewTransaction

import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionInstallment
import com.neoutils.finsight.domain.model.TransactionRecurring
import com.neoutils.finsight.domain.model.TransactionLabel
import com.neoutils.finsight.domain.usecase.impliedRate
import com.neoutils.finsight.extension.closedLegBlockingChange
import com.neoutils.finsight.extension.displayTitleOrNull
import com.neoutils.finsight.ui.model.TransactionLegTarget
import com.neoutils.finsight.ui.model.TransactionLegUi
import com.neoutils.finsight.ui.model.toTransactionLegs
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
     *
     * It declares **no perspective**: showing every monetary leg, this surface reads
     * none of them, so the question a perspective answers — which leg to read by —
     * does not arise. Nor does it name the base currency: the tie-break between the
     * two ends of a cross-currency operation exists for surfaces that must state one
     * figure, and this one states both.
     */
    data class Content(
        val transaction: Transaction,
        val category: Category? = null,
        val creditCard: CreditCard? = null,
        val invoice: Invoice? = null,
        val installment: TransactionInstallment? = null,
        val recurring: TransactionRecurring? = null,
    ) : ViewTransactionUiState {

        /** The transaction's nature (title/colour/icon), derived from the entries. */
        val label: TransactionLabel = transaction.label

        /**
         * The title the transaction has — its own, or its category's — and `null` when
         * it has neither, which is the ordinary case of a transfer and of a payment.
         * The header then says only what the operation *is*.
         */
        val displayTitle: String? = displayTitleOrNull(transaction.title, category)

        val date = transaction.date
        val isCardTarget = transaction.hasLiabilityLeg

        /**
         * One card per monetary leg, from the one owner of that mapping — the verb,
         * the sign policy and the order are all resolved there, off the ledger.
         *
         * [onOpen] is the caller's, because a shortcut is a route and `:core:ui` names
         * none; whether a leg has one at all is the mapper's, and an archived facade
         * has none.
         */
        fun legs(onOpen: ((TransactionLegTarget) -> Unit)? = null): List<TransactionLegUi> =
            transaction.toTransactionLegs(
                invoice = invoice,
                installment = installment,
                onOpen = onOpen,
            )

        /**
         * The rate this operation applied, when it crossed currencies — and `null`
         * whenever it did not, which is every single-currency operation and every one
         * with a single monetary leg (a card purchase has nothing to divide by).
         *
         * The two ends are the ledger's own: the leg money left ([Transaction.primaryEntry],
         * the one owner of "outgoing") and the monetary leg it entered. The conversion legs
         * take no part — they are not monetary, and they hold the rounding residue, not the
         * rate. The direction is the write form's, source → target ([impliedRate]), and it
         * is the direction [legs] orders in, so the arrow between two cards and the
         * quotient agree by construction.
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
         * Derived edit gate: the gates that hold for every operation first, then a
         * decision **by label**. The invoice-status gate (CLOSED/PAID blocks edit
         * *and* delete) is applied one level up.
         *
         * The label is what decides, and not a leg count that serves two purposes at
         * once: the count kept the transfer out *and* the card payment, so relaxing
         * it to admit the first would have admitted the second in silence.
         */
        val isEditable: Boolean = isChangeable &&
            transaction.installmentId == null &&
            when (label) {
                // Edited through the transaction form, which states one money leg —
                // a shape with two is not what that form knows how to write.
                TransactionLabel.EXPENSE, TransactionLabel.INCOME ->
                    transaction.monetaryEntries.size == 1

                // Two monetary legs, and the transfer form states both. A transfer
                // **between currencies** arrives here under the same label: its
                // conversion legs are not monetary and do not change what it is.
                TransactionLabel.TRANSFER -> true

                // Named although the `else` below would refuse it too. While the gate
                // read "exactly one monetary leg", the payment stayed out by the same
                // effect that kept the transfer out; admitting the transfer leaves
                // that count saying nothing about the payment, so what keeps it out
                // has to be said rather than inherited — otherwise "out of scope"
                // quietly becomes "we forgot".
                TransactionLabel.PAYMENT -> false

                // The adjustment today, and any nature that comes to exist tomorrow:
                // born outside editing, and it enters only by being named above.
                else -> false
            }

        val isRemovable: Boolean = isChangeable
    }
}
