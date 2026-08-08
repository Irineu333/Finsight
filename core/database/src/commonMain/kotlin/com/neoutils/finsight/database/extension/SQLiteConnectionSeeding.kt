package com.neoutils.finsight.database.extension

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.neoutils.finsight.domain.model.CurrencySeeding
import com.neoutils.finsight.domain.model.SeedCurrency

/**
 * The seeding itself, as one statement.
 *
 * It is shared by the migration and by the fresh-install callback because a new database
 * never runs a migration: Room creates the schema from the entities, so without this the
 * only user with no currencies at all would be the one who just installed the app.
 *
 * `INSERT OR IGNORE` makes it idempotent and makes the precedence trivial: the seed's own
 * glyph wins over the platform's suggestion for the same code, and running it twice
 * writes nothing the second time.
 *
 * **`name` is left null on purpose.** Storing a name here would freeze it in the language
 * of the first run — switching the app's language would silently stop translating it. A
 * row keeps a name only when the user writes one; otherwise the platform names it at
 * every read.
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
