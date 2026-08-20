package com.neoutils.finsight.database.extension

import androidx.sqlite.SQLiteConnection
import com.neoutils.finsight.database.exception.MigrationAbortedException
import com.neoutils.finsight.database.exception.UnbalancedLedgerException
import com.neoutils.finsight.database.model.UnbalancedTransaction

/**
 * Verifies `Σ entries = 0` for every `(transactionId, currency)` pair, the single
 * invariant that makes every figure in the app derivable from the ledger. Reads only
 * `entries`, so it holds before and after any rewrite of the chart of accounts.
 */
internal fun SQLiteConnection.verifyLedgerBalanced(stage: String) {
    val offenders = unbalancedTransactions()
    if (offenders.isNotEmpty()) throw UnbalancedLedgerException(stage, offenders)
}

/**
 * The offending `(transactionId, currency)` pairs, up to [limit] of them. Exposed apart
 * from [verifyLedgerBalanced] so a test can read them without provoking the exception.
 */
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

/** No entry may point at a dimension that does not exist. */
internal fun SQLiteConnection.verifyNoOrphanDimensions(stage: String) {
    val orphans = scalarLong(
        """
        SELECT COUNT(*) FROM `entries` `e`
        WHERE `e`.`dimensionId` IS NOT NULL
          AND NOT EXISTS (SELECT 1 FROM `dimensions` `d` WHERE `d`.`id` = `e`.`dimensionId`)
        """
    )
    if (orphans != 0L) {
        throw MigrationAbortedException("$stage: $orphans entry(ies) carry a dimension that does not exist")
    }
}

/**
 * `PRAGMA foreign_key_check` over the whole database. Enforcement is off during a
 * migration — it has to be, to rebuild a referenced table — so this is the only
 * moment the keys are actually verified.
 */
internal fun SQLiteConnection.verifyForeignKeys(stage: String) {
    val statement = prepare("PRAGMA foreign_key_check")
    val violations = mutableListOf<String>()
    try {
        while (statement.step()) {
            violations += "${statement.getText(0)} row ${statement.getLong(1)} → ${statement.getText(2)}"
        }
    } finally {
        statement.close()
    }
    if (violations.isNotEmpty()) {
        throw MigrationAbortedException(
            "$stage: foreign key violations — ${violations.take(20).joinToString()}"
        )
    }
}
