package com.neoutils.finsight.mcp

import kotlinx.serialization.json.JsonObject

/**
 * Where a `tools/call` is carried out.
 *
 * The server the window holds has one answer and never asks the question: the process that received
 * the call is the process that owns the database, so it runs the tool itself. A stdio session has to
 * ask it of every call, because whether this process may touch the database is a fact about the
 * *instant* — the window can open or close in the middle of a conversation, and the session outlives
 * either event (design D3).
 *
 * The question is asked here, in one named place, and not spread through the assembly: what varies
 * between the transports is only *who executes*, and the permission gate, the journal and the shape
 * of the answer are the same wherever the execution happened.
 */
internal fun interface McpCallSite {

    /**
     * Answers the call — by taking [here], which is this process's own path through the permission
     * gate and the journal, or by answering in its place.
     *
     * A site that does not take [here] has run nothing and recorded nothing, which is what makes it
     * safe for a process that may not touch the database: the journal is a table of that same
     * database.
     */
    suspend fun answer(
        tool: McpTool,
        arguments: JsonObject?,
        here: suspend () -> McpToolResult,
    ): McpToolResult

    companion object {

        /**
         * This process, always — the site of a server whose own app is holding it open.
         */
        val Here: McpCallSite = McpCallSite { _, _, here -> here() }
    }
}
