package com.neoutils.finsight.database.snapshot

/** What a candidate file is worth, once it has been put through every layer. */
sealed interface CandidateVerification {

    /**
     * The file may replace the archive in use.
     *
     * @param origin what the file says about itself, or `null` when it carries no stamp
     * — a file captured before the stamp existed still restores, and calling that origin
     * unknown is the screen's word, not this module's.
     * @param counts what the file holds, for a person about to overwrite an archive with
     * it.
     */
    data class Accepted(
        val origin: SnapshotOrigin?,
        val counts: ArchiveCounts,
    ) : CandidateVerification

    /** The file may not be used, for the one reason that decided it. */
    data class Rejected(val reason: CandidateRejection) : CandidateVerification
}

/**
 * What a captured file says about where it came from, read back from the stamp the
 * capture wrote into it.
 *
 * The schema version is not among the fields: it travels in the file's own
 * `user_version`, and a second copy would be a second truth about the same fact.
 */
data class SnapshotOrigin(
    val formatVersion: Long,
    val appVersion: String,
    val platform: String,
    val createdAt: Long,
)

/**
 * How much of the user's archive a candidate file holds, counted by facade rather than
 * by table.
 *
 * The shape is the boundary: no table name leaves this module, so an entity added in a
 * future schema is never something a distant caller has to remember.
 */
data class ArchiveCounts(
    val accounts: Long,
    val transactions: Long,
    val categories: Long,
    val creditCards: Long,
)
