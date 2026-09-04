package com.neoutils.finsight.mcp

import com.neoutils.finsight.database.DatabaseOwnership
import com.neoutils.finsight.database.Ownership
import com.neoutils.finsight.feature.mcp.api.AgentActivity
import io.modelcontextprotocol.kotlin.sdk.types.ListToolsResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject

/**
 * The site of a headless process, where the two things that are not settled for the life of a
 * session are asked of every request: **is the app offering its server**, and **may this process
 * touch the database**.
 *
 * **Both are facts about the instant, so both are read at the instant.** A session can last hours.
 * In that time the user can open the app, close it, switch the server off and switch it on again,
 * and a session that had decided any of it when it started would go on being wrong until the client
 * happened to reconnect — announcing a surface the app is not offering, or executing under a switch
 * its owner has turned off (design D3, D7).
 *
 * **The switch is asked first, and the ownership second.** The app's authority is not a fallback for
 * when nothing else answered: a server the user has switched off refuses whatever is or is not
 * holding the database, and it refuses before a connection to the window is even attempted. It also
 * closes the one interval where the two could disagree — the window writes the switch to disk before
 * it takes its socket down, so a call arriving in between must read the choice and not the socket.
 *
 * **A claim that is refused means this process is not the database's**, whether because another one
 * holds it or because the claim could not be made at all — and either way nothing runs here: not the
 * tool, and not the journal entry, which is a row of that same database. The claim is taken per
 * request and given straight back, so a window that wants to open waits at most for one request to
 * finish (design D4).
 *
 * The ownership is a kernel lock and taking it blocks, so it is asked for off the caller's thread.
 */
internal class McpStdioCallSite(
    private val settings: McpServerSettings,
    private val ownership: DatabaseOwnership,
    /**
     * What answers while another process owns the database.
     */
    private val elsewhere: McpCallSite,
) : McpCallSite {

    override suspend fun answer(
        name: String,
        arguments: JsonObject?,
        here: suspend () -> McpToolResult,
    ): McpToolResult {
        if (!offering()) {
            return McpToolResult(
                text = McpServerOff.REFUSAL,
                outcome = AgentActivity.Outcome.REFUSED,
            )
        }

        val owned = claim() ?: return elsewhere.answer(name, arguments, here)

        return try {
            here()
        } finally {
            owned.release()
        }
    }

    override suspend fun list(here: suspend () -> ListToolsResult): ListToolsResult {
        // Nothing at all, which is what a server its owner has switched off has to offer: a list is
        // a promise to run what is on it.
        if (!offering()) return ListToolsResult(tools = emptyList(), nextCursor = null)

        val owned = claim() ?: return elsewhere.list(here)

        return try {
            here()
        } finally {
            owned.release()
        }
    }

    /**
     * The handshake, which says either what the user has granted or that they have switched the
     * whole thing off.
     *
     * Read once, as the session opens, and rightly so: it describes the moment the client connected,
     * and everything it might go stale about — the list, the call — is asked again. See
     * [McpCallSite.instructions].
     */
    override fun instructions(here: () -> String): String =
        if (offering()) here() else McpServerOff.INSTRUCTIONS

    /** Whether the user has the server switched on, as the store holds it at this instant. */
    private fun offering(): Boolean = settings.currentChoice().enabled

    private suspend fun claim(): Ownership? = withContext(Dispatchers.IO) { ownership.tryAcquire() }
}
