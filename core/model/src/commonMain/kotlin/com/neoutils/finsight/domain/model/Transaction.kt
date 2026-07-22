package com.neoutils.finsight.domain.model

import com.neoutils.finsight.extension.cardLeg
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
     * The leg a neutral list looks through: the outgoing one, which is how a
     * transfer or a card payment reads when no perspective is given.
     */
    val primaryEntry: Entry? get() = monetaryEntries.minByOrNull { it.amount }

    /** The transaction's amount, always positive — the sign is a display concern. */
    val amount: Double get() = abs(primaryEntry?.amount ?: 0L) / 100.0

    /** The account the money left, when it left one — a card purchase has none. */
    val sourceAccount: Account? get() = entries.sourceLeg()?.account

    /**
     * The identities a feature resolves its facade from. The ledger hands out the
     * key; what it opens is the feature's business.
     */
    val cardAccountId: Long? get() = entries.cardLeg()?.account?.id
    val invoiceDimensionId: Long? get() = entries.cardLeg()?.dimensionId
    val categoryDimensionId: Long? get() = entries.nominalLeg()?.dimensionId

    val isCardTarget: Boolean get() = entries.any { it.account.type == AccountType.LIABILITY }

    val hasInstallment: Boolean get() = installmentId != null
}
