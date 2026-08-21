package com.neoutils.finsight.database.extension

import androidx.sqlite.SQLiteConnection
import com.neoutils.finsight.database.exception.MigrationAbortedException
import com.neoutils.finsight.database.exception.UnbalancedLedgerException
import com.neoutils.finsight.database.mapper.toEntity
import com.neoutils.finsight.database.model.UnbalancedTransaction
import com.neoutils.finsight.domain.model.DimensionKind

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
 * A dimension may only sit on a leg posting to an account of a nature its kind accepts.
 * Its violation is the silent one: an invoice dimension on a nominal leg breaks no key
 * and still sums to zero — it only makes every total by that dimension wrong.
 *
 * The rule is not restated here. [DimensionKind.landsOn] is read for the pairs it allows,
 * spelled the way the chart of accounts persists them by the single mapper that owns that
 * spelling, and SQL is asked for an entry whose pair is not among them. Reading the file's
 * text as enums would be the same rule twice and would raise on the first `kind` a
 * hand-edited file invents; a pair nobody allows is refused for free.
 */
internal fun SQLiteConnection.verifyDimensionLanding(stage: String) {
    val allowed = DimensionKind.entries
        .flatMap { kind -> kind.landsOn.map { "('${kind.name}', '${it.toEntity().name}')" } }
        .joinToString()
    val statement = prepare(
        """
        SELECT `e`.`id`, `d`.`kind`, `a`.`type`
        FROM `entries` `e`
        JOIN `dimensions` `d` ON `d`.`id` = `e`.`dimensionId`
        JOIN `accounts` `a` ON `a`.`id` = `e`.`accountId`
        WHERE (`d`.`kind`, `a`.`type`) NOT IN (VALUES $allowed)
        LIMIT 20
        """
    )
    val offenders = mutableListOf<String>()
    try {
        while (statement.step()) {
            offenders += "entry ${statement.getLong(0)}: " +
                "${statement.getText(1)} on ${statement.getText(2)}"
        }
    } finally {
        statement.close()
    }
    if (offenders.isNotEmpty()) {
        throw MigrationAbortedException(
            "$stage: dimension(s) landing where they may not — ${offenders.joinToString()}"
        )
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
