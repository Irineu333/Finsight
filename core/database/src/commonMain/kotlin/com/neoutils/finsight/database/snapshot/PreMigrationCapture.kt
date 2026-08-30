package com.neoutils.finsight.database.snapshot

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteException
import androidx.sqlite.driver.bundled.SQLITE_OPEN_READONLY
import com.neoutils.finsight.database.AppSchema
import com.neoutils.finsight.database.extension.declaredSchemaVersion
import com.neoutils.finsight.database.extension.onDatabaseFile

/**
 * Writes what the database at [databasePath] holds into [destinationPath], and does so
 * only while there is still something to write: a file that is there, declaring a version
 * older than this build's.
 *
 * The order is the whole of it. A migration rewrites the archive in place, and Room runs
 * the chain the first time the database is opened — so a copy taken after opening is a
 * copy of the result, and the state it was meant to preserve is already gone. Which is
 * also why the question "is a migration pending" is answered here rather than asked of
 * Room: opening to find out would have migrated.
 *
 * Being handed [destinationPath] *is* the decision to capture, and it is the caller's
 * alone — this module reads no preference to second-guess it, and removing the copy left
 * by the previous migration belongs to whoever chose the path, like all file cleanup here.
 *
 * The connection is read-only, the same way a candidate file is inspected. `VACUUM INTO`
 * only reads its source, so nothing here needs more, and the flag makes it impossible —
 * rather than merely unintended — for the last thing to touch the archive before a
 * migration to be this. A write-ahead log a previous session left behind is read through
 * all the same: SQLite creates the shared memory such a log needs even for a read-only
 * connection, as long as the directory allows it, and the database's own directory does.
 *
 * Nothing here escapes. A capture that cannot happen leaves a line in the log and lets the
 * migration proceed, because the alternative is an app that does not open — and the file it
 * would have copied is still in place, untouched, which is more than the copy promised.
 */
internal fun captureBeforeMigration(
    databasePath: String,
    destinationPath: String,
) {
    try {
        onDatabaseFile(databasePath, SQLITE_OPEN_READONLY) { connection ->
            if (connection.hasPendingMigration()) {
                connection.vacuumInto(destinationPath)
            }
        }
    } catch (cause: SQLiteException) {
        println("$REPORT ${cause.message}")
    }
}

/**
 * Whether opening the file at [databasePath] with this build would migrate it — the same
 * question [captureBeforeMigration] asks itself, answered for whoever has to decide
 * something *before* handing over a destination.
 *
 * There is one such decision and it is [PreMigrationCopyTarget]'s: the copy is written
 * under a single reserved name, so making room for a new one destroys the previous one, and
 * doing that on an opening that turns out to migrate nothing would throw away the copy that
 * is still in force. Asking is what keeps that from happening — and it is asked here, of
 * the same predicate, rather than reimplemented from a schema version and a constant.
 *
 * It reads and creates nothing: a file that is not there, or that SQLite refuses, is
 * answered as no migration pending, which is true of both.
 */
fun isMigrationPending(databasePath: String): Boolean = try {
    onDatabaseFile(databasePath, SQLITE_OPEN_READONLY) { it.hasPendingMigration() }
} catch (cause: SQLiteException) {
    false
}

/**
 * Whether Room is about to migrate this file, read from the file and nowhere else.
 *
 * The floor of 1 is not a guard against odd input: a file declaring 0 is one Room will
 * *create* the schema in, and there is nothing in it yet worth preserving. At or above the
 * ceiling there is nothing to preserve either — the file already holds what this build
 * would migrate it to, or more than this build knows how to read, and Room refuses the
 * downgrade instead of migrating.
 */
private fun SQLiteConnection.hasPendingMigration(): Boolean =
    declaredSchemaVersion() in 1L until AppSchema.VERSION.toLong()

/**
 * The statement [captureInto] runs, over a connection from the driver instead of Room's
 * pool — the pool is precisely what must not exist yet when this runs.
 */
private fun SQLiteConnection.vacuumInto(destinationPath: String) {
    val statement = prepare(VACUUM_INTO)
    try {
        statement.bindText(1, destinationPath)
        statement.step()
    } finally {
        statement.close()
    }
}

/**
 * SQLite's own wording is what gets reported, result code and all: this module has no
 * logger and no `UiText`, nobody is waiting on this line, and every classification of a
 * refused capture is about the `VACUUM` — while the file simply not being there arrives
 * here too, and is no failure at all.
 */
private const val REPORT = "core:database: no copy was taken before migrating —"
