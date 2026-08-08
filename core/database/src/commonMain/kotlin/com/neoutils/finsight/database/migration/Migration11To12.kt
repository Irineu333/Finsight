package com.neoutils.finsight.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.neoutils.finsight.database.extension.verifyForeignKeys
import com.neoutils.finsight.database.extension.verifyLedgerBalanced
import com.neoutils.finsight.database.extension.verifyNoOrphanDimensions

/**
 * Schema 12: a rate stops being *"the currency, against whatever base is in force"* and
 * becomes an observation about a **pair**.
 *
 * **No stored value changes.** `rate`, `date`, `currency` and `source` are read by
 * nothing here. The new column is filled with the base currency in force, and the fill
 * is *exact* rather than approximate: every existing row was measured against that base,
 * which until this schema had no way to change. It is the same quality the budget
 * limit's currency had in [Migration10To11].
 *
 * **The unique index widens rather than moves.** `(currency, date, source)` becomes
 * `(currency, counterCurrency, date, source)`, which is what lets the dollar be observed
 * against the real and against the euro on the same day — two observations, two rows.
 * The names are the canonical ones Room generates, because it is against those that the
 * identity hash check compares.
 *
 * @param baseCurrency the base currency in force, already resolved outside this module —
 * `core/database` cannot reach `Settings` and receives a plain code.
 */
class Migration11To12(
    private val baseCurrency: String,
) : Migration(11, 12) {
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
        connection.verifyLedgerBalanced(stage = "v11 → v12")
        connection.verifyNoOrphanDimensions(stage = "v11 → v12")
        connection.verifyForeignKeys(stage = "v11 → v12")
    }
}
