package com.neoutils.finsight.database.extension

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.neoutils.finsight.domain.model.CurrencySeeding
import com.neoutils.finsight.domain.model.SeedCurrency

/**
 * Fills `currencies` with the shipped seed, the device's currency and every currency an
 * account is already denominated in — one statement, so nothing can land half seeded.
 *
 * Shared by [Migration12To13][com.neoutils.finsight.database.migration.Migration12To13]
 * and by [CurrencySeedingCallback][com.neoutils.finsight.database.callback.CurrencySeedingCallback],
 * because a fresh install never runs a migration and would otherwise be the only database
 * with no currencies at all.
 *
 * `INSERT OR IGNORE` makes it idempotent and settles precedence: the seed's own glyph wins
 * over the platform's suggestion for the same code.
 *
 * `name` is left null on purpose — storing one would freeze it in the language of the
 * first run. A row keeps a name only when the user writes one.
 */
internal fun SQLiteConnection.seedCurrencies(seeding: CurrencySeeding) {
    val inUse = mutableListOf<String>()
    val statement = prepare("SELECT DISTINCT `currency` FROM `accounts`")
    try {
        while (statement.step()) {
            inUse += statement.getText(0)
        }
    } finally {
        statement.close()
    }

    val rows = (seeding.rows() + inUse.map { SeedCurrency(it, seeding.symbolOf(it)) })
        .filter { it.code.isNotBlank() }
        .distinctBy { it.code }

    if (rows.isEmpty()) return

    // `execSQL` binds nothing, so the values are interpolated — and a code or a glyph
    // that could break out of the statement stops here rather than reaching it.
    val values = rows.joinToString(", ") { row ->
        require(!row.code.contains('\'') && !row.symbol.contains('\'')) {
            "a currency code and its symbol may not contain a quote, was '${row.code}'"
        }
        "('${row.code}', '${row.symbol}', NULL, 0)"
    }
    execSQL("INSERT OR IGNORE INTO `currencies` (`code`, `symbol`, `name`, `isArchived`) VALUES $values")
}
