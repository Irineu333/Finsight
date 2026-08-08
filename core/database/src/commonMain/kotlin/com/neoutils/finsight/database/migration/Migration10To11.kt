package com.neoutils.finsight.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.neoutils.finsight.database.extension.verifyForeignKeys
import com.neoutils.finsight.database.extension.verifyLedgerBalanced
import com.neoutils.finsight.database.extension.verifyNoOrphanDimensions

/**
 * Schema 11: the rate archive, and a budget limit that says what it is denominated in.
 *
 * **No stored value changes.** Every existing database is entirely in `'BRL'` — not
 * because anybody chose it, but because it was the model's default — so the currency
 * the new column receives is exactly the one that already denominated each stored
 * limit. The fill is *exact*, not approximate.
 *
 * **And it relabels the legacy chart of accounts**, when asked to. That is
 * re-denomination and not conversion: no value and no balance moves, and `Σ = 0` per
 * currency goes on holding because the currency of every row changes together.
 *
 * **Why a migration and not a startup step.** There is no app initialisation step in
 * this project — `App.kt` only sets the user id, and `EnsureDefaultAccountUseCase` runs
 * fire-and-forget from `DashboardViewModel.init`, concurrent with the dashboard's own
 * flows. A migration has the three properties for free: it runs once, it **records
 * that it ran** through `user_version` with no flag to invent or keep correct, and it
 * precedes every read by construction. The objection that a migration reading the
 * environment stops being deterministic is already settled by a stronger
 * precedent — [Migration3To4] reads the device's clock and time zone. With the
 * parameter, this one is *more* deterministic: a test fixes the argument.
 *
 * **The false positive, and it is accepted.** Someone whose real currency is the legacy
 * one but whose device is set to a foreign region is relabelled **without being asked**,
 * and design D12 means the app offers no way back. Two things narrow it: the **region**
 * decides rather than the language, so an English interface on a Brazilian device fires
 * nothing; and a currency of other than two decimal places is barred, which falls into
 * the silent case of leaving the denomination alone. The alternative considered — asking
 * once, on the first run after updating, which would get both cases right — was
 * rejected in favour of zero friction: it would put a migration screen in front of
 * every user to serve the rare one, and the common case is a user who has been reading
 * their own currency on screen all along and simply keeps reading it.
 *
 * @param relabelCurrency the currency the legacy chart of accounts should be
 * re-denominated to, already resolved and validated outside this module — `core/database`
 * receives a currency code and knows nothing of locales or catalogues. `null` means "do
 * not relabel", which is the common case.
 */
class Migration10To11(
    private val relabelCurrency: String? = null,
) : Migration(10, 11) {
    override fun migrate(connection: SQLiteConnection) {
        // --- 1. The rate archive. A surrogate key with a unique triple, so that a
        //        user's correction and the rate an operation observed can coexist on
        //        the same (currency, date) — which is what makes precedence mean
        //        something instead of destroying the other row. ---
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `exchange_rates` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`currency` TEXT NOT NULL, " +
                "`date` TEXT NOT NULL, " +
                "`rate` REAL NOT NULL, " +
                "`source` TEXT NOT NULL)"
        )
        connection.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_exchange_rates_currency_date_source` " +
                "ON `exchange_rates` (`currency`, `date`, `source`)"
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_exchange_rates_currency_date` " +
                "ON `exchange_rates` (`currency`, `date`)"
        )

        // --- 2. A budget limit becomes denominated. `'BRL'` is not a guess: it is what
        //        every existing limit was already denominated in. The SQL default is
        //        only how SQLite accepts a NOT NULL column on an existing table — the
        //        entity declares none, exactly as `budgets.limitType` already does. ---
        connection.execSQL(
            "ALTER TABLE `budgets` ADD COLUMN `currency` TEXT NOT NULL DEFAULT 'BRL'"
        )

        // --- 3. Re-denominate the legacy chart of accounts, when asked to.
        //
        //        `accounts` **and** `entries`, in this one transaction. An earlier
        //        reading of the design said "no entry is touched", and that was
        //        incompatible with the change itself: if the accounts became USD while
        //        the history went on saying BRL, the per-currency aggregations would
        //        split each account's story in two, and `LedgerBalanceCheck` — which
        //        groups by `(transactionId, currency)` without consulting `accounts` —
        //        would stop being readable as the truth about that account.
        //
        //        Unconditional, with no `WHERE currency = ...`. Every row of both
        //        tables is the legacy denomination today, so the `UPDATE` is exact
        //        either way; unconditional is the form that *cannot* leave two
        //        currencies behind, which is the property that matters.
        //
        //        The system rows go with them. `CLOSED_ACCOUNT`/`CLOSED_CARD` and the
        //        two nominals are rows of the chart like any other, and design D4
        //        wants `Account.currency` to mean the same thing on every line of it.
        if (relabelCurrency != null) {
            // `execSQL` binds nothing, so the code is interpolated — and a code that
            // is not a code stops here rather than reaching the statement. The caller
            // already validated it; this is the module refusing to
            // depend on that being true.
            require(relabelCurrency.matches(Regex("[A-Z]{3}"))) {
                "relabelCurrency must be an ISO 4217 code, was '$relabelCurrency'"
            }
            connection.execSQL("UPDATE `accounts` SET `currency` = '$relabelCurrency'")
            connection.execSQL("UPDATE `entries` SET `currency` = '$relabelCurrency'")
            // A budget limit goes with them, for the same reason and by the same
            // argument. Its denomination was never a choice either — step 2 above filled
            // it with the legacy code because that is what denominated it — so leaving
            // it behind would hand the relabelled user a limit in a currency he holds
            // nothing in, and a progress bar consolidating and marked `≈` forever. That
            // is precisely the cost design D13 exists to keep off the single-currency
            // user, arriving through the migration instead of through the form.
            connection.execSQL("UPDATE `budgets` SET `currency` = '$relabelCurrency'")
        }

        // --- 4. Verification, the same three guards `v7 → v10` closes with. ---
        connection.verifyLedgerBalanced(stage = "v10 → v11")
        connection.verifyNoOrphanDimensions(stage = "v10 → v11")
        connection.verifyForeignKeys(stage = "v10 → v11")
    }
}
