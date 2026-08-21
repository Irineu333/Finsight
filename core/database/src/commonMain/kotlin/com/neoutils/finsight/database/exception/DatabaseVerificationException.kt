package com.neoutils.finsight.database.exception

/**
 * Raised when a candidate file could not be checked at all. Callers decide on [error]
 * alone, while the failure that produced it stays as the cause, so the log keeps the
 * result code and the wording the classification threw away.
 */
class DatabaseVerificationException(
    val error: DatabaseVerificationError,
    cause: Throwable? = null,
) : IllegalStateException(error.message, cause)
