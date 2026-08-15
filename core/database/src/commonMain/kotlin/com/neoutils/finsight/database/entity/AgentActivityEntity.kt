@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * One call of one write tool by an agent — the application's journal, not the ledger.
 *
 * **It lives beside the facades and never inside the ledger.** No rule of the domain
 * branches on who asked for a write, and no figure is computed differently because an
 * agent produced it, so this is not part of the model. It carries no foreign key to
 * `transactions` either: pruning the journal must never take a transaction with it,
 * and a badge that disappears when the record is pruned degrades exactly right — the
 * ledger stays intact.
 *
 * **One row per tool call, not per line written.** A call that records thirty
 * transactions leaves one row naming all thirty in [affected]. Refused calls are
 * recorded too — they are precisely what someone is looking for when investigating why
 * something did not happen — and read-only calls are not recorded at all, because
 * their volume would drown the writes the journal exists for.
 *
 * **The token appears in no column, [arguments] included.**
 */
@Entity(
    tableName = "agent_activity",
    indices = [
        // Both readings are by time: the screen wants the newest rows, and retention
        // deletes the oldest. One descending index serves the first and the same
        // column serves the second.
        Index(value = ["timestamp"]),
    ],
)
data class AgentActivityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** When the call arrived. */
    val timestamp: Instant,
    /**
     * What the client called itself when the connection was initialised.
     *
     * **Nullable, and its absence is not a failure.** A connection can be dropped and
     * resumed without the declaration being repeated, and the next revision of the
     * protocol makes the identification optional and per-request — so a record that
     * required it would need a migration the day that lands.
     *
     * It is also **self-declared and not authenticated**: it says who claimed to be
     * calling, never who was. What authenticates is the token, and the token is the
     * same for every client. Whatever renders this must not present it as verified.
     */
    val client: String?,
    /** The announced name of the tool, as the protocol carries it. */
    val tool: String,
    /**
     * The arguments **as received**, serialised. Not a normalised or re-rendered form:
     * the point of the journal is what was actually asked for.
     */
    val arguments: String,
    val outcome: Outcome,
    /**
     * The identifiers the call touched, serialised as a JSON array of strings.
     *
     * A plain column rather than a converted `List<String>`: the type converters of
     * this database are shared by every facade table, and a list converter added for
     * one journal column would silently become available to all of them. The shape is
     * the mapper's business, and it has exactly one.
     */
    val affected: String,
) {
    /** How the call ended. Refusal and failure are different facts, and both are kept. */
    enum class Outcome {
        /** The tool did what it was asked. */
        OK,

        /** A rule of the domain, or the permission level, refused it. Nothing was written. */
        REFUSED,

        /** It broke — the state of the system says nothing about whether it was allowed. */
        FAILED,
    }
}
