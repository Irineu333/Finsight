package com.neoutils.finsight.domain.ledger

/**
 * Told that a removal is about to happen, **before** the write transaction opens.
 *
 * The ledger has no use for this — a removal needs no announcement to succeed. What
 * it owns, again, is the *timing*, and here the timing is the entire contract: this
 * is said while the rows are still there and from **outside** any write transaction,
 * which is the one moment a caller of `ITransactionRepository` cannot arrange for
 * itself, because where the transaction begins is not visible from out there.
 *
 * Neither of the other two ports can stand in. [TransactionRemovalHook] speaks after
 * the rows are gone and from inside the transaction that removed them — a correction,
 * by construction too late to be a precaution. [DimensionWriteGuard] is asked about a
 * removal from inside that same transaction, and only when dimensions are touched.
 *
 * It is a **third contract** rather than a second form on either, for the reason those
 * two are separate from each other: one form each, one implementer each, and a
 * `fun interface` a test can write as a lambda.
 *
 * No return value and no argument. What a removal is *about* is facade knowledge the
 * ledger does not have and does not need to pass on; an implementation that refuses
 * does so by throwing, and nothing is removed because nothing has begun.
 */
fun interface TransactionRemovalPrelude {
    suspend fun beforeRemoval()

    companion object {
        /**
         * Does nothing, and is this port's default — the one place where this module
         * departs from the other two, whose bindings are mandatory. A missing veto
         * loses a rule and a missing correction leaves a facade describing rows that
         * are gone; a missing prelude leaves nothing in the ledger wrong, because the
         * removal was already complete without it. Silence here is a listener that
         * never asked, not a guarantee quietly dropped.
         */
        val None = TransactionRemovalPrelude { }
    }
}
