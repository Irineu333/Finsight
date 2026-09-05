package com.neoutils.finsight.mcp

import io.modelcontextprotocol.kotlin.sdk.types.ListToolsResult
import kotlinx.serialization.json.JsonObject

/**
 * Where a request is answered — this process, or the one that owns the app right now.
 *
 * The server the window holds has one answer and never asks the question: the process that received
 * the request is the process that owns the database and is the app the user switched on, so it
 * answers itself. A headless session has to ask it of every request, because both halves of the
 * answer are facts about the *instant* — the window can open or close in the middle of a
 * conversation, and the user can switch the server off in the middle of one, and the session
 * outlives either event (design D3, D7).
 *
 * **Everything that varies between the two modes is asked here**, in one named place, and nothing of
 * it is spread through the assembly: the tools, the permission axes, the journal and the shape of an
 * answer are the same wherever the answer came from. That is what keeps the two from drifting, and
 * it is why the three things a session ever answers — the handshake, the list and the call — all
 * pass through this.
 */
internal fun interface McpCallSite {

    /**
     * Answers the call — by taking [here], which is this process's own path through the permission
     * gate and the journal, or by answering in its place.
     *
     * A site that does not take [here] has run nothing and recorded nothing, which is what makes it
     * safe for a process that may not touch the database: the journal is a table of that same
     * database.
     *
     * [name] is what the client asked for, and not a resolved tool, because a site may have to
     * answer for a name this process has never heard of — a call is forwarded, or refused, by name.
     */
    suspend fun answer(
        name: String,
        arguments: JsonObject?,
        here: suspend () -> McpToolResult,
    ): McpToolResult

    /**
     * Answers `tools/list` — by taking [here], this process's own list, or by answering in its
     * place.
     *
     * **The list is asked of the same site as the call because it is the same question**: what this
     * app is offering at this instant. A session that refused its calls and listed from here would
     * announce tools it would not run, and one that forwarded its calls and listed from here would
     * announce the permissions this process read from disk while the window answered from its own —
     * one server saying two things (design D7).
     */
    suspend fun list(here: suspend () -> ListToolsResult): ListToolsResult = here()

    /**
     * What the handshake tells the model before its first question — [here] being what this process
     * would say.
     *
     * **This one is answered once, and that is correct.** The SDK asks for it as the session opens,
     * and what it describes is the session: what the client is connecting to. A client that read
     * "the server is switched off, and only you can switch it on" was told the truth about the
     * moment it connected, and a user who acts on it makes the *next* list and the *next* call true
     * — both of which are asked again. It is also the only one the SDK cannot ask twice: there is
     * one handshake per session, and re-deciding it per request would change nothing that reaches
     * anybody.
     *
     * Not suspending, because the SDK's provider is not: this is read on the thread that opens the
     * session, once.
     */
    fun instructions(here: () -> String): String = here()

    companion object {

        /**
         * This process, always — the site of a server whose own app is holding it open.
         *
         * The switch needs no asking here: the app binds no socket while it is off, so a request
         * that arrived over one was made to a server its owner had switched on.
         */
        val Here: McpCallSite = McpCallSite { _, _, here -> here() }
    }
}
