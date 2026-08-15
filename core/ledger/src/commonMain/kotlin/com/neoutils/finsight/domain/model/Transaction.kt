package com.neoutils.finsight.domain.model

import com.neoutils.finsight.extension.closedLegBlockingChange
import com.neoutils.finsight.extension.liabilityLeg
import com.neoutils.finsight.extension.deriveTransactionLabel
import com.neoutils.finsight.extension.nominalLeg
import com.neoutils.finsight.extension.sourceLeg
import kotlinx.datetime.LocalDate
import kotlin.math.abs

/**
 * A balanced set of ledger [entries] — what the user calls a transaction.
 *
 * Everything the app used to persist about a transaction's nature ([label], its
 * direction, whether it targets a card) is **derived** from the account types of
 * its entries. Nothing here is stored as independent state.
 *
 * It carries no facade either — no category, account, card, invoice, installment or
 * recurring object. Those are not the ledger's to know: a card is the `LIABILITY`
 * leg's account, an invoice and a category are the dimension a leg carries, and the
 * installment and recurring links are the identities below. Each feature resolves
 * the facade it needs from those, which is why the ledger can be read without any of
 * them being available.
 */
data class Transaction(
    val id: Long = 0,
    val title: String?,
    val date: LocalDate,
    // Grouping metadata, not accounting: identities of the facades that produced this
    // transaction. No ledger figure consults them.
    val recurringId: Long? = null,
    val recurringCycle: Int? = null,
    val installmentId: Long? = null,
    val installmentNumber: Int? = null,
    // The balanced double-entry legs of this transaction, each hydrated with its account.
    val entries: List<Entry> = emptyList(),
) {
    /** The transaction's nature, derived from the account types of its entries. */
    val label: TransactionLabel get() = entries.deriveTransactionLabel()

    /**
     * The legs that hold money (`ASSET`/`LIABILITY`), as opposed to the
     * counterpart legs (category, reconciliation).
     */
    val monetaryEntries: List<Entry> get() = entries.filter { it.account.type.isMonetary }

    /**
     * Whether this transaction can be **rewritten in place** — the precondition of
     * `ITransactionRepository.updateTransaction`, stated once, where the facts it reads
     * are.
     *
     * A rewrite deletes every entry and rebuilds from the single monetary leg the caller
     * describes. So it is only correct for a transaction that *has* a single monetary
     * leg: routing a transfer or a card payment through it would drop the second leg
     * silently. The other three gates are the same kind of fact — an adjustment states a
     * balance rather than a movement, one payment of an installment plan is not
     * changeable on its own, and a leg on a closed account cannot receive entries again.
     *
     * It lives here, and not in a screen or in a tool, because it is derivable from the
     * transaction and therefore has exactly one owner. Every consumer decides *whether*
     * it offers the edit; none of them decides *what* the rule is.
     */
    val isRewritable: Boolean get() =
        label != TransactionLabel.ADJUSTMENT &&
            monetaryEntries.size == 1 &&
            installmentId == null &&
            entries.closedLegBlockingChange() == null

    /**
     * The leg a neutral list looks through: the outgoing one — the monetary leg the
     * money *left* — which is how a transfer or a card payment reads when no
     * perspective is given.
     *
     * It says "negative" rather than `min`, and the difference is not a fix: a
     * balanced transaction with two monetary legs has exactly one negative, so `min`
     * already returned it, whatever the currencies. What goes away is a comparison
     * that is only correct because of an invariant it does not state — `min` over
     * `Long` of different currencies. The day two monetary legs share a sign, or
     * someone reasons about this without rebuilding the argument, `min` becomes a
     * silent defect.
     *
     * A transaction with no negative monetary leg (a card purchase, whose only
     * monetary leg is the credited liability) keeps being read through the leg it is
     * read through today.
     */
    val primaryEntry: Entry? get() =
        monetaryEntries.firstOrNull { it.amount < 0 } ?: monetaryEntries.minByOrNull { it.amount }

    /**
     * The transaction's amount as a magnitude. The sign is a display concern and is
     * resolved by the item mapper (`itemDisplayAmount`), which reads it off the leg —
     * an adjustment shows it, a labelled form does not.
     */
    val amount: Double get() = abs(primaryEntry?.amount ?: 0L) / 100.0

    /** The account the money left, when it left one — a card purchase has none. */
    val sourceAccount: Account? get() = entries.sourceLeg()?.account

    /**
     * The identities a feature resolves its facade from, named for the leg they come
     * off rather than for whatever the feature will make of them. The ledger hands
     * out the key; what it opens is none of its business.
     */
    val liabilityAccountId: Long? get() = entries.liabilityLeg()?.account?.id
    val liabilityDimensionId: Long? get() = entries.liabilityLeg()?.dimensionId
    val nominalDimensionId: Long? get() = entries.nominalLeg()?.dimensionId

    /** Whether any leg posts to a liability — a card purchase or its payment. */
    val hasLiabilityLeg: Boolean get() = entries.any { it.account.type == AccountType.LIABILITY }

    /** The same fact for the other monetary nature — whether money moved in an account. */
    val hasAssetLeg: Boolean get() = entries.any { it.account.type == AccountType.ASSET }

    /**
     * Whether any leg posts to a nominal account — what separates a transaction that has an
     * analytic axis at all from a transfer, a card payment or an adjustment, which have none.
     */
    val hasNominalLeg: Boolean get() = entries.any { it.account.type.isNominal }
}
