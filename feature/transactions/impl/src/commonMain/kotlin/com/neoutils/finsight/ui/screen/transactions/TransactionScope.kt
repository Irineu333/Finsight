package com.neoutils.finsight.ui.screen.transactions

import com.neoutils.finsight.domain.model.Transaction

/**
 * The reading perimeter of the transactions screen — the set of monetary accounts
 * both the summary and the list answer for. It is a screen concern, not a ledger
 * one: the ledger already reads any nature; what the screen adds is the decision of
 * which nature it is looking at right now.
 *
 * The three values are the three perimeters, and each one carries the same grammar —
 * opening, flows, closing — differing only in which accounts are inside.
 */
enum class TransactionScope {

    /** Assets and liabilities together: the whole of the user's money. */
    ALL,

    /** The `ASSET` accounts alone — what the screen used to summarise. */
    ACCOUNTS,

    /** The `LIABILITY` accounts alone — the cards. */
    CARDS;

    /**
     * Whether a transaction has a leg inside this perimeter. It is the *only* thing
     * the scope decides about the list: the item itself is still read from its own
     * nature, so the same transaction looks identical in every scope containing it.
     */
    fun contains(transaction: Transaction): Boolean = when (this) {
        ALL -> true
        ACCOUNTS -> transaction.hasAssetLeg
        CARDS -> transaction.hasLiabilityLeg
    }
}
