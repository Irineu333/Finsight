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
