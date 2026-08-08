package com.neoutils.finsight.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.neoutils.finsight.database.extension.seedCurrencies
import com.neoutils.finsight.database.extension.verifyForeignKeys
import com.neoutils.finsight.database.extension.verifyLedgerBalanced
import com.neoutils.finsight.database.extension.verifyNoOrphanDimensions
import com.neoutils.finsight.domain.model.CurrencySeeding

/**
 * Schema 12 → 13: the set of offered currencies becomes a **table**.
 *
 * Creates `currencies` and fills it through
 * [seedCurrencies][com.neoutils.finsight.database.extension.seedCurrencies] in one write:
 * the shipped seed, the device's currency, and every currency an account is already
 * denominated in. A second path to that write would be a second place the user's currency
 * could fail to exist.
 *
 * Reading `accounts.currency` is also what lets this migration and the legacy relabel of
 * [Migration10To11] fit together without either knowing the other: the relabel writes that
 * column, this one reads it.
 *
 * Not published yet.
 *
 * @param seeding resolved outside this module: `core/database` may name neither a locale
 * nor the platform, and receives rows and a glyph rather than the means to derive them.
 */
class Migration12To13(
    private val seeding: CurrencySeeding,
) : Migration(12, 13) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `currencies` (" +
                "`code` TEXT NOT NULL, " +
                "`symbol` TEXT NOT NULL, " +
                "`name` TEXT, " +
                "`isArchived` INTEGER NOT NULL, " +
                "PRIMARY KEY(`code`))"
        )

        connection.seedCurrencies(seeding)

        // --- Verification, the same three guards every migration closes
        //     with. Nothing here touches the ledger, and that is exactly what they
        //     assert. ---
        connection.verifyLedgerBalanced(stage = "v12 → v13")
        connection.verifyNoOrphanDimensions(stage = "v12 → v13")
        connection.verifyForeignKeys(stage = "v12 → v13")
    }
}
