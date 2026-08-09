package com.neoutils.finsight.database.exception

/**
 * Raised when a migration reached a state it promised it could not reach. Thrown
 * from inside `migrate()`, it makes Room roll back the whole transaction — which is
 * the point: a migration that rewrote accounting history must never commit half of
 * it, and it must not commit an ending it cannot justify either.
 */
class MigrationAbortedException(reason: String) : IllegalStateException(reason)
