package com.neoutils.finsight.database.exception

/**
 * Raised when the database could not take on the content of a file. Callers decide on
 * [error] alone, while the failure that produced it stays as the cause, so the log keeps
 * the result code and the wording the classification threw away.
 */
class DatabaseRestoreException(
    val error: DatabaseRestoreError,
    cause: Throwable? = null,
) : IllegalStateException(error.message, cause)
