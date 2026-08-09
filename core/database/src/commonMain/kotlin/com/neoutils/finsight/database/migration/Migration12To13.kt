package com.neoutils.finsight.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.neoutils.finsight.database.extension.verifyForeignKeys
import com.neoutils.finsight.database.extension.verifyLedgerBalanced
import com.neoutils.finsight.database.extension.verifyNoOrphanDimensions

/**
 * Schema 12 → 13: a rate becomes an observation about a **pair**.
 *
 * `exchange_rates` gains `counterCurrency`, and the unique index widens from
 * `(currency, date, source)` to `(currency, counterCurrency, date, source)` so the dollar
 * can be observed against the real and against the euro on the same day. The index names
 * are the ones Room generates, because it is against those that the identity hash
 * compares.
 *
 * No stored value changes: the back-fill is exact, since every existing row was measured
 * against the base in force, which until this schema had no way to change.
 *
 * Not published yet.
 *
 * @param baseCurrency the base in force, resolved outside this module — `core/database`
 * cannot reach `Settings` and receives a plain code.
 */
class Migration12To13(
    private val baseCurrency: String,
) : Migration(12, 13) {
    override fun migrate(connection: SQLiteConnection) {
        // `execSQL` binds nothing, so the code is interpolated — and a code that is not
        // a code stops here rather than reaching the statement. The caller resolved it
        // above this module; this is it refusing to depend on that being true.
        require(baseCurrency.matches(Regex("[A-Z]{3}"))) {
            "baseCurrency must be an ISO 4217 code, was '$baseCurrency'"
        }

        // --- 1. The counterpart becomes explicit. The SQL default is only how SQLite
        //        accepts a NOT NULL column on an existing table — the entity declares
        //        none, deliberately: a row that does not say its pair is the defect. ---
        connection.execSQL(
            "ALTER TABLE `exchange_rates` ADD COLUMN `counterCurrency` TEXT NOT NULL DEFAULT ''"
        )
        connection.execSQL("UPDATE `exchange_rates` SET `counterCurrency` = '$baseCurrency'")

        // --- 2. The indices follow the pair. ---
        connection.execSQL("DROP INDEX IF EXISTS `index_exchange_rates_currency_date_source`")
        connection.execSQL("DROP INDEX IF EXISTS `index_exchange_rates_currency_date`")
        connection.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS " +
                "`index_exchange_rates_currency_counterCurrency_date_source` " +
                "ON `exchange_rates` (`currency`, `counterCurrency`, `date`, `source`)"
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_exchange_rates_currency_counterCurrency_date` " +
                "ON `exchange_rates` (`currency`, `counterCurrency`, `date`)"
        )

        // --- 3. Verification, the same three guards every migration closes with. ---
        connection.verifyLedgerBalanced(stage = "v12 → v13")
        connection.verifyNoOrphanDimensions(stage = "v12 → v13")
        connection.verifyForeignKeys(stage = "v12 → v13")
    }
}
