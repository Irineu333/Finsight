package com.neoutils.finsight.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.neoutils.finsight.database.extension.verifyForeignKeys
import com.neoutils.finsight.database.extension.verifyLedgerBalanced
import com.neoutils.finsight.database.extension.verifyNoOrphanDimensions

/**
 * Schema 14 → 15: the journal of what an agent wrote becomes a **table**.
 *
 * It only creates `agent_activity` and its index. Nothing is backfilled and nothing is
 * read: before this version no agent could write, so an empty journal is the truthful
 * state of every existing database.
 *
 * The table stands alone on purpose — no foreign key to `transactions`, no dimension,
 * no column added to the ledger. Retention prunes it, and pruning it must not be able
 * to take a transaction with it.
 */
object Migration14To15 : Migration(14, 15) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `agent_activity` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`timestamp` INTEGER NOT NULL, " +
                "`client` TEXT, " +
                "`tool` TEXT NOT NULL, " +
                "`arguments` TEXT NOT NULL, " +
                "`outcome` TEXT NOT NULL, " +
                "`affected` TEXT NOT NULL)"
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_agent_activity_timestamp` " +
                "ON `agent_activity` (`timestamp`)"
        )

        // --- Verification, the same three guards every migration closes with. This one
        //     touches nothing but a new table, and that is exactly what they assert. ---
        connection.verifyLedgerBalanced(stage = "v14 → v15")
        connection.verifyNoOrphanDimensions(stage = "v14 → v15")
        connection.verifyForeignKeys(stage = "v14 → v15")
    }
}
