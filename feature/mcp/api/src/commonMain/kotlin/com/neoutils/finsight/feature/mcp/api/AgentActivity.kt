@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.feature.mcp.api

import kotlinx.coroutines.flow.Flow
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** How a tool call ended. Refusal and failure are different facts, and both are kept. */
enum class AgentActivityOutcome {
    /** The tool did what it was asked. */
    OK,

    /**
     * A rule of the domain, or the permission level, refused it. **Nothing was written.**
     * These are precisely what someone is looking for when investigating why something did
     * not happen, which is why they are recorded rather than dropped.
     */
    REFUSED,

    /** It broke — the state of the system says nothing about whether it was allowed. */
    FAILED,
}

/**
 * One call of one write tool by an agent — the application's journal, not the ledger.
 *
 * **One row per tool call, not per line written.** A call that records thirty transactions
 * leaves one record naming all thirty in [affected], and a call that was refused leaves one
 * too. **Read-only calls produce no record at all**: their volume would drown the writes the
 * journal exists for.
 *
 * **The token appears in no field of this record, [arguments] included.** What authenticates
 * is the token, and a journal that carried it would be a second place to leak it from.
 *
 * The journal is not part of the model: no rule of the domain branches on who asked for a
 * write, and no balance, spending, invoice or net-worth figure ever reads it. Removing
 * records leaves every transaction they described intact.
 */
data class AgentActivity(
    /** Assigned by the store when the record is appended; `0` on a record not yet written. */
    val id: Long = 0,
    /** When the call arrived. */
    val timestamp: Instant,
    /**
     * What the client called itself when the connection was initialised.
     *
     * **Nullable, and its absence is not a failure.** A connection can be dropped and
     * resumed without the declaration being repeated, and the next revision of the protocol
     * makes the identification optional and per-request — so a record that demanded it would
     * need a migration the day that lands.
     *
     * It is **self-declared and not authenticated**: it says who claimed to be calling,
     * never who was. What authenticates is the token, and the token is the same for every
     * client. Whatever renders this must not present it as a verified fact.
     */
    val client: String?,
    /** The announced name of the tool, as the protocol carries it. */
    val tool: String,
    /**
     * The arguments **as received**, serialised. Not a normalised or re-rendered form: the
     * point of the journal is what was actually asked for. The token is never among them.
     */
    val arguments: String,
    val outcome: AgentActivityOutcome,
    /**
     * The identifiers the call touched — all of them, however many rows it wrote. They are
     * what carries the badge on a line and the way from a record to the entity it created,
     * where the inverse operation already lives when the domain offers one.
     */
    val affected: List<String>,
)

/** The single owner of the agent activity journal. */
interface IAgentActivityRepository {

    /**
     * The most recent [limit] records, newest first, emitting as new ones arrive so the
     * screen updates without the user acting.
     */
    fun observeRecent(limit: Int): Flow<List<AgentActivity>>

    /**
     * Appends one record for one tool call — successful, refused or failed alike. Never
     * called for a read.
     */
    suspend fun record(activity: AgentActivity)

    /**
     * Drops every record older than [olderThan] — the declared retention policy.
     *
     * The journal keeps arguments as received, which includes whole statements with
     * descriptions, amounts and counterparties; without a deadline it would be a second,
     * perpetual copy of the user's financial history.
     */
    suspend fun prune(olderThan: Instant)
}
