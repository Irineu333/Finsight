package com.neoutils.finsight.database

import androidx.sqlite.SQLiteConnection
import com.neoutils.finsight.domain.model.CURRENCY_SEED
import com.neoutils.finsight.domain.model.CurrencySeeding
import com.neoutils.finsight.domain.model.SeedCurrency

/**
 * The seeding with the device taken out of it: the seed, and the code as its own glyph.
 *
 * A test that needs the locale's row states it, rather than inheriting whichever machine
 * runs the suite — the migration receives resolved rows precisely so this is possible.
 */
internal fun testSeeding(
    locale: SeedCurrency? = null,
) = object : CurrencySeeding {
    override fun rows(): List<SeedCurrency> = CURRENCY_SEED + listOfNotNull(locale)
    override fun symbolOf(code: String): String = code
}

internal fun SQLiteConnection.tableExists(tableName: String): Boolean {
    val stmt = prepare(
        "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='$tableName'"
    )
    stmt.step()
    val exists = stmt.getLong(0) > 0
    stmt.close()
    return exists
}

internal fun SQLiteConnection.indexExists(indexName: String): Boolean {
    val stmt = prepare(
        "SELECT COUNT(*) FROM sqlite_master WHERE type='index' AND name='$indexName'"
    )
    stmt.step()
    val exists = stmt.getLong(0) > 0
    stmt.close()
    return exists
}

internal fun SQLiteConnection.getColumns(tableName: String): List<String> {
    val stmt = prepare("PRAGMA table_info(`$tableName`)")
    val columns = mutableListOf<String>()
    while (stmt.step()) {
        columns.add(stmt.getText(1))
    }
    stmt.close()
    return columns
}
