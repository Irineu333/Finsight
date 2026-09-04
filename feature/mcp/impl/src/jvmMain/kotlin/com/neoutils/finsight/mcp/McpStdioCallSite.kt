package com.neoutils.finsight.mcp

import com.neoutils.finsight.database.DatabaseOwnership
import com.neoutils.finsight.feature.mcp.api.AgentActivity
import com.neoutils.finsight.mcp.surface.AgentRefusal
import com.neoutils.finsight.mcp.tool.agentJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject

/**
 * The site of a headless process: it runs the call itself while it can own the database, and hands
 * it to [elsewhere] while it cannot.
 *
 * **The ownership is taken per call and given back immediately.** A stdio session can last hours
 * and asks nothing of the database between calls, so holding the claim for the length of the
 * conversation would keep the user's own window from opening for as long as their agent stayed
 * connected. Taken and released around each call, the window waits at most for one call to finish
 * (design D4), and the answer to "may I execute" is read at the moment it matters rather than
 * remembered from the start of the session.
 *
 * **A claim that is refused means this process is not the database's**, whether because another one
 * holds it or because the claim could not be made at all — and either way nothing runs here: not the
 * tool, and not the journal entry, which is a row of that same database.
 *
 * The ownership is a kernel lock and taking it blocks, so it is asked for off the caller's thread.
 */
internal class McpStdioCallSite(
    private val ownership: DatabaseOwnership,
    /**
     * What answers while another process owns the database.
     */
    private val elsewhere: McpCallSite = NotWhileAnotherProcessOwnsTheDatabase,
) : McpCallSite {

    override suspend fun answer(
        tool: McpTool,
        arguments: JsonObject?,
        here: suspend () -> McpToolResult,
    ): McpToolResult {
        val owned = withContext(Dispatchers.IO) { ownership.tryAcquire() }
            ?: return elsewhere.answer(tool, arguments, here)

        return try {
            here()
        } finally {
            owned.release()
        }
    }
}

/**
 * What a headless process answers when the database is another process's, until it has somewhere to
 * send the call instead.
 *
 * The refusal is the app's own dialect — the reason, and where the user resolves it — and it is
 * marked as a refusal rather than raised as a fault, so the agent reads it and says so instead of
 * reporting that the app is broken. Nothing about it reaches the journal: writing a row is exactly
 * what this process may not do.
 */
internal val NotWhileAnotherProcessOwnsTheDatabase: McpCallSite = McpCallSite { _, _, _ ->
    McpToolResult(
        text = agentJson.encodeToString(
            AgentRefusal(
                reason = "The Finsight app is open on this machine, and while it is, it is the app " +
                    "itself that answers an agent — this session may not touch the archive behind " +
                    "its back. Tell the user the app does this and that it is their own open " +
                    "window standing between the two, and try again.",
            ),
        ),
        outcome = AgentActivity.Outcome.REFUSED,
    )
}
