package com.neoutils.finsight.domain.model

import kotlinx.datetime.LocalDate

/**
 * What the user asked for, before the ledger exists.
 *
 * This is the **input** side of the write boundary, and it speaks only identities:
 * an account id, a dimension id, an account nature (design D6). It names no facade —
 * resolving "this card" or "this category" into an id belongs to the feature that
 * owns it, which is the only place that knows what the facade is.
 *
 * `LedgerEntryWriter` is the single translator from an intent to its balanced
 * [Entry] set. It still *completes* a one-sided intent, because doing so creates the
 * system account on demand and applies the one sign rule the app has — both of which
 * exist exactly once, at that boundary.
 */
data class TransactionIntent(
    val title: String?,
    val date: LocalDate,
    val recurringId: Long? = null,
    val recurringCycle: Int? = null,
    val installmentId: Long? = null,
    val installmentNumber: Int? = null,
    val legs: List<TransactionLeg>,
    /**
     * How to complete a one-sided intent. Required when [legs] holds a single leg,
     * ignored when the legs already balance.
     */
    val contra: ContraLeg? = null,
)

/**
 * One side of the user's intent, by identity.
 *
 * [type] is the retained *input* vocabulary (design D4): it is what the user picked,
 * and it is the only thing that decides the entry's sign. Everything else is a
 * ledger identity — [accountId] is the row the entry posts to, [dimensionId] the
 * analytic axis it is classified by (an invoice's, on a card leg).
 */
data class TransactionLeg(
    val type: TransactionType,
    val amount: Double,
    val accountId: Long,
    val dimensionId: Long? = null,
)

/**
 * The counterpart of a one-sided intent, named by what it *is* to the ledger rather
 * than by the facade that chose it: an account nature and, optionally, the dimension
 * the leg is classified by.
 *
 * [nature] is the documented exception to the Derivation Rule (design D4): "this is
 * an expense" is the user's declaration, carried by the category, and nothing in the
 * ledger derives it. The writer resolves the nature to the single system account of
 * that nature — the two nominals and reconciliation — creating it on demand.
 */
data class ContraLeg(
    val nature: AccountType,
    val dimensionId: Long? = null,
)
