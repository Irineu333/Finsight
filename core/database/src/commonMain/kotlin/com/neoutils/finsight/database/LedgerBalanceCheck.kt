package com.neoutils.finsight.database

import androidx.sqlite.SQLiteConnection

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

data class UnbalancedTransaction(
    val transactionId: Long,
    val currency: String,
    val sum: Long,
)

/**
 * Verifies `Σ entries = 0` for every `(transactionId, currency)` pair, the single
 * invariant that makes every figure in the app derivable from the ledger. Reads only
 * `entries`, so it holds before and after any rewrite of the chart of accounts.
 */
internal fun SQLiteConnection.verifyLedgerBalanced(stage: String) {
    val offenders = unbalancedTransactions()
    if (offenders.isNotEmpty()) throw UnbalancedLedgerException(stage, offenders)
}

internal fun SQLiteConnection.unbalancedTransactions(limit: Int = 20): List<UnbalancedTransaction> {
    val statement = prepare(
        """
        SELECT `transactionId`, `currency`, SUM(`amount`) AS `total`
        FROM `entries`
        GROUP BY `transactionId`, `currency`
        HAVING `total` <> 0
        LIMIT $limit
        """
    )
    val offenders = mutableListOf<UnbalancedTransaction>()
    try {
        while (statement.step()) {
            offenders += UnbalancedTransaction(
                transactionId = statement.getLong(0),
                currency = statement.getText(1),
                sum = statement.getLong(2),
            )
        }
    } finally {
        statement.close()
    }
    return offenders
}
