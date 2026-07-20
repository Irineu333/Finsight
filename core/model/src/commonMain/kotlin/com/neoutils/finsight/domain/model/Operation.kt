package com.neoutils.finsight.domain.model

import com.neoutils.finsight.extension.deriveOperationLabel
import kotlinx.datetime.LocalDate
import kotlin.math.abs

/**
 * A balanced set of ledger [entries] — what the user calls a transaction.
 *
 * Everything the app used to persist about an operation's nature ([label], its
 * direction, whether it targets a card) is **derived** from the account types of
 * its entries. Nothing here is stored as independent state.
 */
data class Operation(
    val id: Long = 0,
    val title: String?,
    val date: LocalDate,
    val recurring: OperationRecurring? = null,
    val category: Category? = null,
    val sourceAccount: Account? = null,
    val targetCreditCard: CreditCard? = null,
    val targetInvoice: Invoice? = null,
    val installment: OperationInstallment? = null,
    // The balanced double-entry legs of this operation, each hydrated with its account.
    val entries: List<Entry> = emptyList(),
) {
    val displayTitle
        get() = title?.takeIf { it.isNotBlank() } ?: category?.name?.takeIf { it.isNotBlank() } ?: "Untitled"

    /** The operation's nature, derived from the account types of its entries. */
    val label: OperationLabel get() = entries.deriveOperationLabel()

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

    /** The operation's amount, always positive — the sign is a display concern. */
    val amount: Double get() = abs(primaryEntry?.amount ?: 0L) / 100.0

    val isCardTarget: Boolean get() = entries.any { it.account.type == AccountType.LIABILITY }

    val hasInstallment: Boolean get() = installment != null
}
