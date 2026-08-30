package com.neoutils.finsight.database.extension

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

/**
 * Asks a question about the file at [path], on a connection of its own straight from the
 * driver, closed whatever [block] does.
 *
 * A connection of its own is the point rather than a detail. SQLite reports a damaged file
 * as corruption *of the connection*, so reading an unknown file on the pool that serves the
 * app would fire that against production; and nothing here goes through Room, which is what
 * makes it usable before the migrations have run — opening through Room to find out would
 * already have migrated.
 *
 * [flags] never carries `SQLITE_OPEN_CREATE`, and that is the contract rather than a
 * preference: with it, a path naming no file is not an error — SQLite *creates* the file
 * and hands back something that reads as a database with nothing in it, so a question
 * about an existing file would manufacture its own answer. Without it, a missing file is
 * refused, which is also the only way this module has of telling whether a file is there:
 * it has no file API and is not getting one.
 */
internal fun <T> onDatabaseFile(
    path: String,
    flags: Int,
    block: (SQLiteConnection) -> T,
): T {
    val connection = BundledSQLiteDriver().open(path, flags)
    return try {
        block(connection)
    } finally {
        connection.close()
    }
}
