package com.neoutils.finsight.database.exception

/**
 * Raised when a capture could not produce its file. Callers decide on [error] alone,
 * while the SQLite failure stays as the cause, so the log keeps the result code and
 * the wording the classification threw away.
 */
class DatabaseCaptureException(
    val error: DatabaseCaptureError,
    cause: Throwable? = null,
) : IllegalStateException(error.message, cause)
