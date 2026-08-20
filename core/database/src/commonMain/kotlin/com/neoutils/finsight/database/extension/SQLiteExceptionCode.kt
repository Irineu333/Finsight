package com.neoutils.finsight.database.extension

import androidx.sqlite.SQLiteException

/**
 * The SQLite result code behind a refusal, or `null` when the message carries none.
 *
 * `androidx.sqlite` keeps the code inside the message and nowhere else: [SQLiteException]
 * is a plain `RuntimeException(message)` on JVM and native, and an alias for
 * `android.database.SQLException` on Android, and neither exposes a code. Reading it out
 * of the wording lives here so the wording is understood in one place.
 */
internal fun SQLiteException.resultCode(): Int? =
    RESULT_CODE.find(message.orEmpty())?.groupValues?.get(1)?.toIntOrNull()

private val RESULT_CODE = Regex("""Error code: (\d+)""")

/** The bytes read are a database, and one whose pages no longer add up. */
internal const val SQLITE_CORRUPT = 11

/** There is no room left on the device. */
internal const val SQLITE_FULL = 13

/** The bytes read are not a database. */
internal const val SQLITE_NOTADB = 26
