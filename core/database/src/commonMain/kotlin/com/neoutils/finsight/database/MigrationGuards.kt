package com.neoutils.finsight.database

import androidx.sqlite.SQLiteConnection

/**
 * Raised when a migration reached a state it promised it could not reach. Thrown
 * from inside `migrate()`, it makes Room roll back the whole transaction — which is
 * the point: a migration that rewrote accounting history must never commit half of
 * it, and it must not commit an ending it cannot justify either.
 */
class MigrationAbortedException(reason: String) : IllegalStateException(reason)

/** No entry may point at a dimension that does not exist. */
internal fun SQLiteConnection.verifyNoOrphanDimensions(stage: String) {
    val orphans = count(
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

private fun SQLiteConnection.count(sql: String): Long {
    val statement = prepare(sql)
    try {
        statement.step()
        return statement.getLong(0)
    } finally {
        statement.close()
    }
}
