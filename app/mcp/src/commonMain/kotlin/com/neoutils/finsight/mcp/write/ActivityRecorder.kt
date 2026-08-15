@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.mcp.write

import com.neoutils.finsight.feature.mcp.api.AgentActivity
import com.neoutils.finsight.feature.mcp.api.AgentActivityOutcome
import com.neoutils.finsight.feature.mcp.api.IAgentActivityRepository
import com.neoutils.finsight.mcp.contract.McpTool
import com.neoutils.finsight.mcp.contract.ToolErrorCategory
import com.neoutils.finsight.mcp.contract.ToolOutcome
import com.neoutils.finsight.mcp.contract.isWrite
import kotlinx.serialization.json.JsonObject
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.ExperimentalTime

/**
 * Writes the application's journal: **one row per call of a write tool, refusals
 * included**.
 *
 * One row per *call* and not per line written: a call that records thirty transactions
 * leaves a single record naming all thirty, because what someone investigates later is the
 * act, not its rows.
 *
 * **Reads produce nothing.** Their volume would drown the writes the journal exists for,
 * and a read changed nothing to investigate.
 *
 * **The token appears in no field, the arguments included.** What authenticates is the
 * token, and a journal that carried it would be a second place to leak it from. The
 * arguments are recorded as received, which is the point — what was actually asked for —
 * and that is also why they have a deadline: they contain whole statements, with
 * descriptions, amounts and counterparties.
 *
 * @param declaredClient what the client called itself when it initialised the connection.
 * **Self-declared and not authenticated**: it says who claimed to be calling, never who
 * was. `null` is a defined state — a connection can be dropped and resumed without the
 * declaration being repeated — and never a failure.
 */
class ActivityRecorder(
    private val journal: IAgentActivityRepository,
    private val clock: Clock,
    private val declaredClient: () -> String? = { null },
    /** How long a record is kept. Declared here because the journal has no deadline of its own. */
    val retention: Duration = DEFAULT_RETENTION,
) {

    /**
     * Records how a call of [tool] ended, if [tool] writes.
     *
     * @param arguments the arguments as received. They are serialised verbatim; nothing is
     * normalised, and the token is not among them because it travels in a header and never
     * reaches a tool.
     * @param affected the identifiers the call touched — all of them, however many rows it
     * wrote. They are the way from a record to the entity it created, where the inverse
     * operation already lives when the domain offers one.
     */
    suspend fun record(
        tool: McpTool,
        arguments: JsonObject,
        outcome: ToolOutcome,
        affected: List<String> = emptyList(),
    ) {
        if (!tool.isWrite) return

        journal.record(
            AgentActivity(
                timestamp = clock.now(),
                client = declaredClient(),
                tool = tool.name,
                arguments = arguments.toString(),
                outcome = outcome.asActivityOutcome(),
                affected = affected,
            ),
        )
    }

    /** Drops every record past its deadline — the retention policy, applied. */
    suspend fun prune() = journal.prune(olderThan = clock.now() - retention)

    companion object {

        /**
         * How long the journal keeps a record.
         *
         * A declared deadline is a requirement and not a tuning knob: the records hold the
         * arguments as received, so a journal without one is a second, perpetual copy of
         * the user's financial history. Ninety days is long enough to answer "why did this
         * happen" about something noticed a season later.
         */
        val DEFAULT_RETENTION: Duration = 90.days
    }
}

/**
 * How a tool call ended, in the journal's vocabulary.
 *
 * **Refused and failed are different facts and both are kept.** A refusal — by a rule of
 * the domain, by invalid input, by a conflict or by the permission level — means nothing
 * was written, and it is precisely what someone looks for when investigating why something
 * did not happen. A failure says nothing about whether the operation was allowed.
 */
internal fun ToolOutcome.asActivityOutcome(): AgentActivityOutcome = when (this) {
    is ToolOutcome.Ok -> AgentActivityOutcome.OK
    is ToolOutcome.Failed -> when (error.category) {
        ToolErrorCategory.DOMAIN_RULE,
        ToolErrorCategory.NOT_FOUND,
        ToolErrorCategory.INVALID_INPUT,
        ToolErrorCategory.CONFLICT,
        ToolErrorCategory.PERMISSION,
        -> AgentActivityOutcome.REFUSED

        ToolErrorCategory.UNAVAILABLE,
        ToolErrorCategory.INTERNAL,
        -> AgentActivityOutcome.FAILED
    }
}
