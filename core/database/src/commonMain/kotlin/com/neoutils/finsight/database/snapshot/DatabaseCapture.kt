package com.neoutils.finsight.database.snapshot

import androidx.room.useWriterConnection
import androidx.sqlite.SQLiteException
import com.neoutils.finsight.database.AppDatabase
import com.neoutils.finsight.database.exception.DatabaseCaptureError
import com.neoutils.finsight.database.exception.DatabaseCaptureException

/**
 * Writes everything this database holds into [destinationPath], as a file that opens on
 * its own.
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
 * @throws DatabaseCaptureException when SQLite refuses to write the file.
 */
suspend fun AppDatabase.captureInto(destinationPath: String) {
    try {
        useWriterConnection { connection ->
            connection.usePrepared("VACUUM INTO ?1") { statement ->
                statement.bindText(1, destinationPath)
                statement.step()
            }
        }
    } catch (cause: SQLiteException) {
        throw DatabaseCaptureException(cause.toCaptureError(), cause)
    }
}

/**
 * `androidx.sqlite` carries the SQLite result code inside the message and nowhere else:
 * `SQLiteException` is a plain `RuntimeException(message)` on JVM and native, and an
 * alias for `android.database.SQLException` on Android, and neither exposes a code.
 *
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
    val resultCode = RESULT_CODE.find(report)?.groupValues?.get(1)?.toIntOrNull()
    return when {
        resultCode == SQLITE_FULL -> DatabaseCaptureError.NO_SPACE
        resultCode == SQLITE_NOTADB -> DatabaseCaptureError.DESTINATION_EXISTS
        "output file already exists" in report -> DatabaseCaptureError.DESTINATION_EXISTS
        "SQL statements in progress" in report -> DatabaseCaptureError.STATEMENT_IN_PROGRESS
        else -> DatabaseCaptureError.UNKNOWN
    }
}

private val RESULT_CODE = Regex("""Error code: (\d+)""")

private const val SQLITE_FULL = 13
private const val SQLITE_NOTADB = 26
