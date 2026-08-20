package com.neoutils.finsight.database.snapshot

/**
 * Why a candidate file may not take the place of the archive in use. The message is
 * English and meant for the log: this module knows nothing of `UiText`, so saying any of
 * this to a person belongs to whoever offers the replacement as a feature.
 *
 * The causes are told apart because the screen answers each of them differently — "this
 * app is out of date" is not "this file is broken" — and because the operation they
 * guard is irreversible, so a refusal the user cannot act on is a refusal that will be
 * retried with the same file.
 */
enum class CandidateRejection(val message: String) {
    NOT_A_DATABASE("The path holds no SQLite database"),
    CORRUPTED("The database is structurally damaged"),
    NOT_FROM_THIS_APP("The database was not written by this app"),
    SCHEMA_TOO_NEW("The database comes from a newer version of this app"),
    SCHEMA_MISMATCH("The database does not carry the schema this app expects"),
    UNBALANCED_LEDGER("The ledger does not sum to zero for every transaction and currency"),
    ORPHAN_DIMENSION("An entry points at a dimension the database does not hold"),
    FOREIGN_KEY_VIOLATION("The database holds rows pointing at rows that do not exist"),
}
