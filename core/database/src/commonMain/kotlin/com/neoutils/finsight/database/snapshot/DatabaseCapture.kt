@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.database.snapshot

import androidx.room.useWriterConnection
import androidx.sqlite.SQLiteException
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.neoutils.finsight.database.AppDatabase
import com.neoutils.finsight.database.exception.DatabaseCaptureError
import com.neoutils.finsight.database.exception.DatabaseCaptureException
import com.neoutils.finsight.database.extension.SQLITE_FULL
import com.neoutils.finsight.database.extension.SQLITE_NOTADB
import com.neoutils.finsight.database.extension.resultCode
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Writes everything this database holds into [destinationPath], as a file that opens on
 * its own and says where it came from.
 *
 * Copying the database file is not an alternative: the app runs in write-ahead logging,
 * where the main file alone may not carry so much as the schema. `VACUUM INTO` leaves a
 * single file — no `-wal`, no `-shm` — carrying the schema version, the `AUTOINCREMENT`
 * counters and the schema identity that makes Room recognise the file as a database of
 * this very app, and showing only what was committed when the capture ran.
 *
 * `VACUUM` refuses to run inside a transaction and while another statement is open on
 * the same connection, which is why the statement is the only one the writer connection
 * is holding. It also refuses a [destinationPath] that already holds a file with
 * content.
 *
 * [appVersion] and [platform] are the caller's to supply — this module knows the
 * database, not the app running it — and land in [SnapshotMeta] alongside the instant
 * of the capture. The schema version is not among them: it already travels in
 * `user_version`, and writing it again would be a second truth about the same fact.
 *
 * @throws DatabaseCaptureException when SQLite refuses to write the file or to stamp it.
 */
suspend fun AppDatabase.captureInto(
    destinationPath: String,
    appVersion: String,
    platform: String,
) {
    try {
        useWriterConnection { connection ->
            connection.usePrepared(VACUUM_INTO) { statement ->
                statement.bindText(1, destinationPath)
                statement.step()
            }
        }
    } catch (cause: SQLiteException) {
        throw DatabaseCaptureException(cause.toCaptureError(), cause)
    }

    withContext(Dispatchers.Default) {
        stampOrigin(destinationPath, appVersion, platform)
    }
}

/**
 * The one statement that produces a captured file, named once because two places run it:
 * this capture, on Room's writer connection, and the one taken before a migration, on a
 * connection from the driver because Room's pool must not exist yet.
 */
internal const val VACUUM_INTO = "VACUUM INTO ?1"

/**
 * Writes [SnapshotMeta] into the file the capture has just produced, over a throwaway
 * connection straight from the driver.
 *
 * Room is deliberately not used here. It opens in write-ahead logging, which would put
 * a `-wal` and a `-shm` back beside the file and undo the one property `VACUUM INTO`
 * was chosen for; a driver connection leaves the `journal_mode = delete` the file was
 * born with. The connection is closed in a `finally` because it is the only hold on the
 * file, and the file is what the caller is about to hand elsewhere.
 *
 * A failure here leaves the file where it is: this module has no file API and is not
 * getting one — cleaning up after a refused capture belongs to whoever chose the path.
 *
 * Blocking, and called on the context the database itself runs its queries on: a
 * `suspend` capture invoked from a view model's scope must not write to disk on the
 * caller's thread.
 */
private fun stampOrigin(
    destinationPath: String,
    appVersion: String,
    platform: String,
) {
    try {
        val connection = BundledSQLiteDriver().open(destinationPath)
        try {
            connection.execSQL(SnapshotMeta.CREATE)
            val statement = connection.prepare(SnapshotMeta.INSERT)
            try {
                statement.bindLong(1, SnapshotMeta.FORMAT_VERSION)
                statement.bindText(2, appVersion)
                statement.bindText(3, platform)
                statement.bindLong(4, Clock.System.now().toEpochMilliseconds())
                statement.step()
            } finally {
                statement.close()
            }
        } finally {
            connection.close()
        }
    } catch (cause: SQLiteException) {
        throw DatabaseCaptureException(cause.toStampError(), cause)
    }
}

/**
 * The code decides wherever it can. A full disk is 13. A 26 is the destination holding
 * bytes that are not a database — the only file `VACUUM INTO` opens, so nothing else in
 * this statement can produce it. Three refusals share the code 1 and only their own
 * wording separates them: a destination that is already a database, a statement still
 * open on this connection, and a capture asked for from inside a transaction. The last
 * has no constant of its own, because it is a caller's mistake rather than an outcome
 * to report — it arrives as [DatabaseCaptureError.UNKNOWN], with the wording kept in
 * the cause.
 */
private fun SQLiteException.toCaptureError(): DatabaseCaptureError {
    val report = message.orEmpty()
    val resultCode = resultCode()
    return when {
        resultCode == SQLITE_FULL -> DatabaseCaptureError.NO_SPACE
        resultCode == SQLITE_NOTADB -> DatabaseCaptureError.DESTINATION_EXISTS
        "output file already exists" in report -> DatabaseCaptureError.DESTINATION_EXISTS
        "SQL statements in progress" in report -> DatabaseCaptureError.STATEMENT_IN_PROGRESS
        else -> DatabaseCaptureError.UNKNOWN
    }
}

/**
 * A refused stamp is not a refused `VACUUM`, so it does not borrow that classification:
 * by the time the stamp runs the destination exists and is a database of this app's own
 * making, which is what every constant [toCaptureError] names is about. Running out of
 * room while the row is written remains possible, and is the one worth naming.
 */
private fun SQLiteException.toStampError(): DatabaseCaptureError = when (resultCode()) {
    SQLITE_FULL -> DatabaseCaptureError.NO_SPACE
    else -> DatabaseCaptureError.UNKNOWN
}
