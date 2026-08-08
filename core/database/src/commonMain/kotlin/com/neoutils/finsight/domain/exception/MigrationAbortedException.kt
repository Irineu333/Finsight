package com.neoutils.finsight.domain.exception

/**
 * Raised when a migration reached a state it promised it could not reach. Thrown from
 * inside `migrate()`, it makes Room roll back the whole transaction.
 */
class MigrationAbortedException(reason: String) : IllegalStateException(reason)
