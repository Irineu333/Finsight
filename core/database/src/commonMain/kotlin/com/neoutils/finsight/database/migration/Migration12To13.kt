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
 * Schema 13: the set of offered currencies stops being an opinion embedded in the code
 * and becomes a **table**.
 *
 * **One write, not two.** The seed, every currency an existing account is already
 * denominated in, and the currency the device's locale names all land in the same
 * `INSERT`. Under an overlay design these would be two migrations with distinct purposes
 * — seed, and materialise what is in use — but with a single table the destination is the
 * same, so they are the same operation.
 *
 * The consequence that matters most: **the locale's auto-registration stops being a
 * mechanism.** There is no "automatic registration" to design, test or explain — there is
 * a seeding, and the device's currency is among what it seeds. A second path to that same
 * write would be a second place the user's currency could fail to exist.
 *
 * **Nobody loses the currency they already use.** `SELECT DISTINCT currency FROM accounts`
 * is what shrinks the shipped set from twenty-two to six without taking ARS from the
 * Argentinian who already has an account in it. It is also what makes this migration and
 * the legacy relabel of [Migration10To11] fit together **without either knowing the other**:
 * the relabel writes `accounts.currency`, and this reads it. No ordering is required, and
 * none could be arranged — the relabel is `10 → 11` and this can only be `12 → 13`.
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
