package com.neoutils.finsight.domain.ledger

/**
 * A write about to reach the ledger, described in the only terms the ledger has.
 *
 * [dimensionIds] are the sub-ledgers the write touches — the ones it adds entries
 * to, or takes them away from. [settlesALiability] is the ledger shape of paying a
 * bill: an asset leg and a liability leg on the same transaction.
 */
data class LedgerWrite(
    val dimensionIds: Set<Long>,
    val settlesALiability: Boolean,
)

/**
 * The veto the owner of a set of dimensions may exercise over a write that touches
 * them, checked at the single write boundary.
 *
 * The ledger has no rule of its own to apply here: whether a sub-ledger still
 * accepts movement is the facade's business, and a closed invoice is the example
 * that exists (design D11). What the ledger does own is *where* the question is
 * asked — one point, so two screens cannot disagree about what is editable, which
 * is exactly the divergence `InvoiceWriteGuardTest` was written after.
 *
 * Implementations refuse by throwing a typed error. No return value: a guard that
 * could be ignored would not be a boundary.
 */
fun interface DimensionWriteGuard {
    suspend fun ensureAccepts(write: LedgerWrite)

    companion object {
        /**
         * Accepts everything. Not a default — the ledger's Koin module requires a
         * binding, so an app without one fails to start rather than silently
         * skipping every veto. This is for tests whose subject is elsewhere.
         */
        val None = DimensionWriteGuard { }
    }
}
