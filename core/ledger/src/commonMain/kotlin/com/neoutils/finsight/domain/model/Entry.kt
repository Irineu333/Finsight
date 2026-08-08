package com.neoutils.finsight.domain.model

/**
 * A single leg of a balanced [Transaction].
 *
 * [amount] is signed and expressed in the currency's minor unit (e.g. cents),
 * following the debit-positive convention: a positive amount debits the account,
 * a negative amount credits it. For every currency present in a transaction, the
 * sum of its entries' amounts is exactly zero.
 */
data class Entry(
    val id: Long = 0,
    val transactionId: Long? = null,
    val account: Account,
    val amount: Long,
    // The analytic axis this leg is tagged with, if any — the sub-ledger it belongs
    // to inside its account. A facade's total is Σ entries carrying its dimension.
    val dimensionId: Long? = null,
) {
    /**
     * The currency this leg is denominated in — **derived** from its account, never
     * held beside it.
     *
     * A leg posts to an account, and an account has exactly one currency; two fields
     * where there is one fact are two sources that can disagree. Deriving makes the
     * leg in a divergent currency unutterable on the way *out* too, not only at the
     * write boundary, and leaves the many sites that build an `Entry` with no
     * decision to take.
     *
     * The **column** `entries.currency` stays, and not out of inertia:
     * `balanced-ledger` requires the invariant to be verifiable *reading only the
     * entries*, and `LedgerBalanceCheck` verifies it with a `GROUP BY (transactionId,
     * currency)` that never touches `accounts`. Deriving in the model and persisting
     * in the table is not duplication — it is the difference between what the model
     * guarantees and what an integrity check must be able to read on its own.
     */
    val currency: String get() = account.currency
}
