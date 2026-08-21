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
    override fun rows(): List<SeedCurrency> =
        CURRENCY_SEED.map { SeedCurrency(it, symbolOf(it)) } + listOfNotNull(locale)
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

/**
 * Every row of every table, rendered as text and keyed by table name.
 *
 * A migration that claims to change nothing is worth more than a spot check of the columns
 * someone thought to look at: comparing this before and after fails on any value that moved,
 * in any table, including the ones the migration was not supposed to know about.
 */
internal fun SQLiteConnection.dumpAllTables(): Map<String, List<String>> =
    tableNames().associateWith { table ->
        val rows = mutableListOf<String>()
        val stmt = prepare("SELECT * FROM `$table` ORDER BY `rowid`")
        try {
            while (stmt.step()) {
                rows += (0 until stmt.getColumnCount()).joinToString("|") { column ->
                    if (stmt.isNull(column)) "<null>" else stmt.getText(column)
                }
            }
        } finally {
            stmt.close()
        }
        rows
    }

/** Every table, index and trigger the database declares, by name. */
internal fun SQLiteConnection.schemaObjectNames(): Set<String> {
    val names = mutableSetOf<String>()
    val stmt = prepare("SELECT `type` || ':' || `name` FROM `sqlite_master` WHERE `name` NOT LIKE 'sqlite_%'")
    try {
        while (stmt.step()) names += stmt.getText(0)
    } finally {
        stmt.close()
    }
    return names
}

private fun SQLiteConnection.tableNames(): List<String> {
    val names = mutableListOf<String>()
    val stmt = prepare(
        "SELECT `name` FROM `sqlite_master` " +
            "WHERE `type` = 'table' AND `name` NOT LIKE 'sqlite_%' ORDER BY `name`"
    )
    try {
        while (stmt.step()) names += stmt.getText(0)
    } finally {
        stmt.close()
    }
    return names
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
