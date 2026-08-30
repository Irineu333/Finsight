package com.neoutils.finsight.database.extension

import androidx.sqlite.SQLiteConnection

/**
 * The first column of the first row of [sql], as a `Long`. Closes the statement whatever
 * happens, which is the whole reason a counting query does not spell itself out at every
 * call site.
 */
internal fun SQLiteConnection.scalarLong(sql: String): Long {
    val statement = prepare(sql)
    try {
        statement.step()
        return statement.getLong(0)
    } finally {
        statement.close()
    }
}

/**
 * The schema version the file itself declares — what Room reads to decide whether to
 * create, to migrate, or to open as it is.
 *
 * It travels inside the file rather than beside it, which is what lets it be read without
 * Room and what makes a captured file carry its own version. Zero is the file no schema
 * was ever written into.
 */
internal fun SQLiteConnection.declaredSchemaVersion(): Long = scalarLong("PRAGMA user_version")
