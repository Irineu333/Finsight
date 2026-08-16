package com.neoutils.finsight.mcp

import com.neoutils.finsight.feature.mcp.api.AgentActivity
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.JsonObject

/**
 * One tool the server offers an agent.
 *
 * Every tool the app will ever offer is one of these, and the server knows nothing else about them:
 * it announces them, hands them their arguments, and records what they did. A tool translates and
 * composes; the rule it applies belongs to whichever use case already owns it.
 */
internal interface McpTool {

    /** The stable identity the agent calls it by, and the identity the activity log keeps. */
    val name: String

    /** What it does, in the words the agent reads before deciding to call it. */
    val description: String

    /** What it takes, so the agent fills the arguments in rather than guessing at them. */
    val inputSchema: ToolSchema

    /** Whether calling it changes anything — the one thing that decides if it leaves a trace. */
    val effect: McpToolEffect

    suspend fun call(arguments: JsonObject?): McpToolResult
}

/**
 * What a tool does to the app's data.
 *
 * This is not a permission axis and not a category of tool: it is the answer to a single question
 * the activity log asks of every execution. An agent asks dozens of questions to answer one, and
 * recording those would bury the handful of acts that actually changed something.
 */
internal enum class McpToolEffect {

    /** Answers a question and alters nothing. Never recorded — there is nothing to audit. */
    READS,

    /** Changes something, or is refused while trying to. Always recorded, either way. */
    CHANGES,
}

/**
 * What an execution produced: what the agent is told, and — when the tool was one that changes
 * things — what the log keeps about it.
 *
 * [summary], [detail] and [reference] are ignored for a [McpToolEffect.READS] tool, which is why
 * they carry defaults: a read has no act to describe.
 */
internal data class McpToolResult(
    /** What the agent gets back. */
    val text: String,
    /** How the act ended: it went through, or permission or the domain said no. */
    val outcome: AgentActivity.Outcome = AgentActivity.Outcome.APPLIED,
    /**
     * What the act was about, in the user's words, as they were true at that instant — the account,
     * the category or the card named the way the user names them, never an identifier on its own.
     */
    val summary: String = "",
    /** Why it was refused. `null` when it went through: there is nothing to explain. */
    val detail: String? = null,
    /** What it created or changed, so the user can reach the posting from the log. */
    val reference: AgentActivity.Reference? = null,
)
