package com.neoutils.finsight.feature.mcp.api

import kotlinx.coroutines.flow.Flow

/**
 * The agent activity log: acts go in, the section reads them newest first, and the user may
 * empty it.
 *
 * There is no update and no removal of a single act. A record that could be edited would stop
 * being a record, and the two things that may remove one — the declared retention and the
 * user's own clearing — both act on the log as a whole.
 */
interface IAgentActivityRepository {

    /**
     * The newest [limit] acts, newest first — the recent activity the section shows without
     * being asked for it.
     */
    fun observeRecent(limit: Int = DEFAULT_RECENT_LIMIT): Flow<List<AgentActivity>>

    /** The whole log, newest first — the full history the section offers access to. */
    fun observeAll(): Flow<List<AgentActivity>>

    /**
     * Records an act and answers the identity it was given.
     *
     * The instant is stamped here rather than supplied, so *when* has one owner. Nothing is
     * de-duplicated: a repeated call is a second act, and showing both is the point.
     */
    suspend fun record(
        operation: String,
        summary: String,
        outcome: AgentActivity.Outcome,
        detail: String? = null,
        reference: AgentActivity.Reference? = null,
    ): Long

    /**
     * Empties the log at the user's request.
     *
     * It removes the record of what was done and **nothing that was done**: every posting the
     * discarded acts produced stays exactly where it is. That is what makes clearing safe to
     * offer at all — the log is a trace, and the ledger is the truth.
     */
    suspend fun clear()

    companion object {
        /**
         * How many acts "recent" means — enough to cover a session's worth of work at a
         * glance, with the rest a step away through [observeAll].
         */
        const val DEFAULT_RECENT_LIMIT: Int = 50
    }
}
