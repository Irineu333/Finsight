package com.neoutils.finsight.mcp

import com.neoutils.finsight.feature.mcp.api.AgentActivity
import com.neoutils.finsight.feature.mcp.api.IAgentActivityRepository
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.JsonObject

/**
 * The one door every tool call goes through, and the only place the activity log is written from.
 *
 * It is a door rather than a call each tool remembers to make: a tool that forgot would vanish from
 * the record while still changing the ledger, and the record is the only place authorship of a
 * write ever appears. Reactivity delivers the result — the transaction simply shows up on the
 * screen — and says nothing about where it came from.
 *
 * **A read never becomes an entry.** The decision is the tool's [McpToolEffect] and not the
 * outcome, so a read that fails is still a read, and stays out. What is left is what changed
 * something or tried to, which is exactly what the user came to the log to see.
 *
 * **A repeat is a second act.** Nothing here de-duplicates, because the duplication that a missing
 * idempotency permits is precisely what the log exists to expose: the two identical postings sit
 * side by side, with their times.
 *
 * **An unauthorised request never reaches this.** Someone who presented no token is not an agent
 * whose acts are being audited, and letting any request write a row would hand a stranger the
 * ability to fill the log.
 */
internal class AgentActivityJournal(
    private val activity: IAgentActivityRepository,
) {

    suspend fun execute(tool: McpTool, arguments: JsonObject?): McpToolResult {
        val result = try {
            tool.call(arguments)
        } catch (cause: Throwable) {
            // Rethrows only a cancellation of *this* call, and never one that arrived from a job
            // the tool started and lost: that is a failure to record, not a caller going away.
            currentCoroutineContext().ensureActive()
            // A tool that throws instead of refusing is a defect, and the trace still has to exist:
            // the user's question is why the agent said it could not do something, and silence is
            // the one answer that never helps. The readable summary is what is lost, so the
            // operation's own name stands in for it.
            McpToolResult(
                text = FAILURE_TEXT,
                outcome = AgentActivity.Outcome.REFUSED,
                summary = tool.name,
                detail = cause.message ?: cause::class.simpleName ?: FAILURE_TEXT,
            )
        }

        return record(tool, result)
    }

    /**
     * A call turned away before the tool was reached, because the permission it needs is withheld.
     *
     * It is kept for the same reason a refusal the domain raised is: the user's question is *why did
     * the agent say it could not do that*, and "it asked to remove something and a switch of yours
     * stopped it" is the answer. The tool never runs, so nothing changed — which is exactly what the
     * entry records.
     */
    suspend fun refuse(tool: McpTool, refusal: McpToolResult): McpToolResult = record(tool, refusal)

    private suspend fun record(tool: McpTool, result: McpToolResult): McpToolResult {
        if (tool.effect == McpToolEffect.CHANGES) {
            activity.record(
                operation = tool.name,
                summary = result.summary,
                outcome = result.outcome,
                detail = result.detail,
                reference = result.reference,
            )
        }

        return result
    }

    private companion object {
        const val FAILURE_TEXT = "The operation could not be completed."
    }
}
