package com.neoutils.finsight.mcp

import com.neoutils.finsight.mcp.surface.AgentRefusal
import com.neoutils.finsight.mcp.tool.agentJson
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ListToolsRequest
import io.modelcontextprotocol.kotlin.sdk.types.ListToolsResult
import io.modelcontextprotocol.kotlin.sdk.types.Method
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import java.util.concurrent.ConcurrentHashMap

/**
 * The server a headless process speaks with when the user has switched the MCP server off.
 *
 * **It speaks, and that is the point.** The window simply binds no socket when the switch is off,
 * so the question never arises there; a process a client launched has already been launched, and
 * exiting would show the agent a program that died — which it would report as the app being broken,
 * or missing, and never as a switch of the user's own. So the handshake completes, `tools/list`
 * answers with nothing, and every call is refused in words that name the switch and where it lives
 * (design D7).
 *
 * **Nothing of the app is assembled here.** No tool is registered, no journal is reachable and the
 * database is never opened: there is nothing this process is allowed to do, so it holds nothing it
 * could do it with. The two handlers are installed per session rather than left to the SDK's own,
 * because the SDK answers a call on an unregistered name with *"tool not found"* — the exact false
 * statement about the app that the notice next door exists to prevent.
 */
internal object McpServerOff {

    fun newServer(): Server {
        val server = Server(
            // The same identity the running server presents: the client is talking to this app
            // either way, and what differs is only what the app is willing to do.
            serverInfo = Implementation(
                name = McpSessionFactory.SERVER_NAME,
                version = McpSessionFactory.SERVER_VERSION,
            ),
            options = ServerOptions(
                // Declared, and empty. A client told the server has no tools capability at all
                // would have no `tools/list` to ask, and would read the silence as an app that
                // does nothing rather than one that was switched off.
                capabilities = ServerCapabilities(tools = ServerCapabilities.Tools(listChanged = false)),
            ),
            instructionsProvider = { INSTRUCTIONS },
        )

        /** The sessions already prepared, so a second `onConnect` does not prepare them twice. */
        val prepared = ConcurrentHashMap.newKeySet<String>()

        server.onConnect {
            server.sessions.values.forEach { session ->
                if (prepared.add(session.sessionId)) {
                    session.setRequestHandler<ListToolsRequest>(Method.Defined.ToolsList) { _, _ ->
                        ListToolsResult(tools = emptyList(), nextCursor = null)
                    }
                    session.setRequestHandler<CallToolRequest>(Method.Defined.ToolsCall) { _, _ ->
                        CallToolResult(
                            content = listOf(TextContent(REFUSAL)),
                            // A refusal the agent has to read and act on, not a fault of the
                            // transport: the app is working exactly as its owner asked it to.
                            isError = true,
                        )
                    }
                }
            }
        }

        return server
    }

    /** What the agent is told before its first question, so it never asks one. */
    private val INSTRUCTIONS: String = buildString {
        append(McpPermissionNotice.WHAT_THIS_IS)
        append(" Its MCP server is switched off right now: nothing is offered here and no call ")
        append("will run.\n\n")
        append("Only this user can switch it on, in ")
        append(McpPermissionNotice.THE_SECTION)
        append(". When they ask for something the app does, tell them the app does it and is ")
        append("waiting on them to switch the server on, and where to do it. Never tell them the ")
        append("app cannot do it.")
    }

    /** What every call is answered with, in the same words and for the same reason. */
    private val REFUSAL: String = agentJson.encodeToString(
        AgentRefusal(
            reason = "The Finsight MCP server is switched off. The operation exists and nothing " +
                "here will run until this user switches the server on, in " +
                "${McpPermissionNotice.THE_SECTION}. Say the app does this and is waiting on " +
                "their permission — never that it cannot.",
        ),
    )
}
