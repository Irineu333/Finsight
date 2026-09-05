package com.neoutils.finsight.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.neoutils.finsight.database.extension.verifyForeignKeys
import com.neoutils.finsight.database.extension.verifyLedgerBalanced
import com.neoutils.finsight.database.extension.verifyNoOrphanDimensions

/**
 * Schema 14 → 15: the agent activity log becomes a table.
 *
 * It is the smallest migration the project has: one `CREATE TABLE`, one index, and not a single
 * statement that reads or writes anything that already exists. The table is born empty and
 * fills only when an agent acts, so a device that never enables the server carries eight empty
 * columns and nothing else.
 *
 * The reference columns carry no foreign key, and that is deliberate: the log records that
 * something was created or changed, and it must never be the reason a posting cannot be
 * deleted.
 *
 * @see com.neoutils.finsight.database.entity.AgentActivityEntity
 */
object Migration14To15 : Migration(14, 15) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `agent_activity` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`at` INTEGER NOT NULL, " +
                "`operation` TEXT NOT NULL, " +
                "`summary` TEXT NOT NULL, " +
                "`outcome` TEXT NOT NULL, " +
                "`detail` TEXT, " +
                "`referenceKind` TEXT, " +
                "`referenceId` INTEGER)"
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_agent_activity_at` ON `agent_activity` (`at`)"
        )

        // --- Verification, the same three guards every migration closes with. Here they
        //     assert the strongest claim this hop makes: it added a table and left the
        //     ledger exactly as it found it. ---
        connection.verifyLedgerBalanced(stage = "v14 → v15")
        connection.verifyNoOrphanDimensions(stage = "v14 → v15")
        connection.verifyForeignKeys(stage = "v14 → v15")
    }
}
