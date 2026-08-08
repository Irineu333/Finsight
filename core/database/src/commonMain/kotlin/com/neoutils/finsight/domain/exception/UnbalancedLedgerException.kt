package com.neoutils.finsight.domain.exception

import com.neoutils.finsight.domain.model.UnbalancedTransaction

/**
 * Raised when `entries` violates the double-entry invariant. Thrown from inside
 * `migrate()`, it makes Room roll back the whole migration transaction.
 */
class UnbalancedLedgerException(
    val stage: String,
    val offenders: List<UnbalancedTransaction>,
) : IllegalStateException(
    "Ledger is unbalanced at '$stage': " +
        offenders.joinToString { "transaction ${it.transactionId} (${it.currency}) sums to ${it.sum}" }
)
