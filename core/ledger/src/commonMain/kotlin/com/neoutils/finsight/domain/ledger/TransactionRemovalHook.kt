package com.neoutils.finsight.domain.ledger

import com.neoutils.finsight.domain.model.Transaction

/**
 * Told that a transaction was removed, inside the write transaction that removed it.
 *
 * The ledger has no use for this — a removal is complete the moment the rows are
 * gone. What it does own is the *timing*: a facade whose own state describes those
 * rows has to be corrected atomically with them, or it ends up describing
 * transactions that no longer exist.
 *
 * This is deliberately a **second contract**, separate from [DimensionWriteGuard],
 * rather than a second form on it (design D11). One contract with two forms would
 * be an abstraction bought by symmetry; two contracts of one form each, with one
 * implementer each, keep both cheap.
 *
 * It exists as a port for the reason the veto does, though not for the same kind of
 * reason: three removal paths reach it — a single installment transaction, a whole
 * installment, and a future invoice's transactions — and a rule reimplemented per
 * caller is the divergence this design keeps refusing.
 */
fun interface TransactionRemovalHook {
    /** [transaction] as it was, entries included: after the call its rows are gone. */
    suspend fun onRemoved(transaction: Transaction)

    companion object {
        /**
         * Does nothing. Not a default — the ledger's Koin module requires a binding,
         * so a facade that forgets to register loses its correction at startup, not
         * silently at the first removal. This is for tests whose subject is elsewhere.
         */
        val None = TransactionRemovalHook { }
    }
}
