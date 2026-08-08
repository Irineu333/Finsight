package com.neoutils.finsight.database.exception

import com.neoutils.finsight.database.model.UnbalancedTransaction

/**
 * Raised when `entries` violates the double-entry invariant. Thrown from inside
 * `migrate()`, it makes Room roll back the whole migration transaction — which is
 * the point: a migration that rewrote accounting history must never commit half of
 * it. Also thrown from tests, over the same SQL.
 */
class UnbalancedLedgerException(
    val stage: String,
    val offenders: List<UnbalancedTransaction>,
) : IllegalStateException(
    "Ledger is unbalanced at '$stage': " +
        offenders.joinToString { "transaction ${it.transactionId} (${it.currency}) sums to ${it.sum}" }
)
