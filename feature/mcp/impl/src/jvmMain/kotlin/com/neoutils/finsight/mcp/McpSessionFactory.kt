package com.neoutils.finsight.mcp

import com.neoutils.finsight.feature.mcp.api.AgentActivity
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.ServerSession
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ListToolsRequest
import io.modelcontextprotocol.kotlin.sdk.types.ListToolsResult
import io.modelcontextprotocol.kotlin.sdk.types.Method
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import java.util.concurrent.ConcurrentHashMap

/**
 * Assembles the [Server] a client session talks to, whichever transport carries it.
 *
 * **What a session is does not depend on how it arrived.** The tools registered, the axis filter on
 * what is announced and on what is executed, the journal every call goes through, and the
 * instructions the handshake carries are the same over a socket and over this process's own
 * standard streams. None of them mentions a port, so none of them belongs to a transport — and
 * writing them once is what keeps the two from answering differently for the same permissions.
 *
 * **Where the line falls.** This knows a session only as the object the SDK hands it: [newServer]
 * reports each one through `onSessionOpen` and keeps no register of its own. Counting the clients
 * the app is talking to, disconnecting them, and telling them the tool list moved are the business
 * of whoever holds the socket — they answer questions ("how many are connected", "end them") that a
 * session reached through a pipe cannot be asked, and the caller that can answer them is the one
 * already holding the sessions it opened.
 *
 * **The permission is applied twice, deliberately.** `tools/list` answers with the tools of the
 * granted axes alone, and a call on any other is refused before the tool is reached: the
 * announcement is a consequence of the permission, not its only application (design D5). Both read
 * the same set at the instant of the request, so there is no registry to keep in step with the
 * switches. What is withheld is still declared in the handshake, because a filtered list alone makes
 * a withheld capability look like one the app does not have (design D13).
 */
internal class McpSessionFactory(
    private val settings: McpServerSettings,
    private val journal: AgentActivityJournal,
    /**
     * What the server offers. It is empty while the surface is being built; a server with no tools
     * still speaks the protocol, and answers `tools/list` with the truth about itself.
     */
    private val tools: List<McpTool> = emptyList(),
    /**
     * Where a request is answered — the list as much as the call. [McpCallSite.Here] is this
     * process, which is the only answer a server its own app is holding open ever has; a headless
     * session asks the question of every request, because whether it may touch the database is a
     * fact about the instant (design D3).
     */
    private val calls: McpCallSite = McpCallSite.Here,
) {

    /**
     * One [Server] per client session, which is the shape the transports ask for: the streamable
     * HTTP endpoint calls this once for each connection that arrives without a session of its own,
     * and a stdio process calls it once, for the only session it will ever have.
     *
     * [onSessionOpen] is invoked once per session, at the one moment the session exists and is
     * reachable, with the handlers of this assembly already in place.
     */
    fun newServer(onSessionOpen: (ServerSession) -> Unit = {}): Server {
        val server = Server(
            serverInfo = Implementation(name = SERVER_NAME, version = SERVER_VERSION),
            options = ServerOptions(
                capabilities = ServerCapabilities(
                    // The permission axes decide which tools are announced, so the list changes
                    // while a client is connected and the client has to be told (design D5).
                    tools = ServerCapabilities.Tools(listChanged = true),
                ),
            ),
            // Read per session, at the moment it opens, so a client that connects after the user
            // moved a switch is told what is true now. It is the channel that reaches the model
            // before its first question, which is what makes it the place a withheld capability can
            // be declared at all (design D13).
            instructionsProvider = {
                calls.instructions { McpPermissionNotice.instructions(settings.permissions.value) }
            },
        )

        /** The sessions already prepared, so a second `onConnect` does not prepare them twice. */
        val prepared = ConcurrentHashMap.newKeySet<String>()

        server.onConnect {
            // The only moment the session exists and is reachable: `onConnect` runs at the end of
            // the SDK's own session setup, with the session already registered — and before the
            // transport hands it the first message, so the handlers installed below are in place
            // for every request the client will ever make on it.
            server.sessions.values.forEach { session ->
                if (prepared.add(session.sessionId)) {
                    session.setRequestHandler<ListToolsRequest>(Method.Defined.ToolsList) { _, _ ->
                        calls.list { grantedToolList() }
                    }
                    session.setRequestHandler<CallToolRequest>(Method.Defined.ToolsCall) { request, _ ->
                        answer(request)
                    }
                    onSessionOpen(session)
                }
            }
        }

        return server
    }

    /**
     * What **this process** announces: the tools of the granted axes, and no trace of the others.
     *
     * Computed per request rather than registered once, so a client that re-lists after being told
     * the list changed reads the answer the switch has just produced. It is offered to the call
     * site rather than returned to the client, because a session whose calls are answered elsewhere
     * has to be listed from there too.
     */
    private fun grantedToolList(): ListToolsResult {
        val granted = settings.permissions.value
        return ListToolsResult(
            tools = tools
                .filter { it.axis in granted }
                .map { Tool(name = it.name, description = it.description, inputSchema = it.inputSchema) },
            nextCursor = null,
        )
    }

    /**
     * What a `tools/call` is answered with.
     *
     * The dispatch is this assembly's own rather than the SDK's registry, because the call site has
     * to be consulted for **every** name and the registry answers on its own for the ones it does
     * not hold. A process whose app is switched off must refuse the whole surface in the app's own
     * words, and it cannot do that by leaving a name to a registry (design D7).
     *
     * Nothing else about the SDK's dispatch is reproduced here: a tool that throws is the journal's
     * business, and it is the journal that turns it into an answer rather than letting it escape.
     */
    private suspend fun answer(request: CallToolRequest): CallToolResult {
        val result = calls.answer(request.name, request.arguments) {
            // Not announced is not the same as not enforced. A tool called by name without its
            // permission is refused here, before anything of it runs, and the refusal says the
            // operation *exists and is not authorised* — an agent told the name is unknown reports
            // back that the app cannot do the thing, which is false and hides the switch that would
            // fix it.
            when (val tool = tools.firstOrNull { it.name == request.name }) {
                null -> unknown(request.name)
                else -> if (tool.axis in settings.permissions.value) {
                    journal.execute(tool, request.arguments)
                } else {
                    journal.refuse(tool, McpPermissionNotice.refusal(tool))
                }
            }
        }

        return CallToolResult(
            content = listOf(TextContent(result.text)),
            // A refusal is the tool's own answer and not a protocol fault: the agent has to read it
            // and change course, which it cannot do with a transport-level error.
            isError = result.outcome == AgentActivity.Outcome.REFUSED,
        )
    }

    /**
     * A name this app has no operation for, said the way the SDK says it.
     *
     * The one case where *not found* is the truth, and it stays the truth in both modes and either
     * position of the switch: what the app does not do, it does not do. It is the refusal
     * `McpPermissionNotice` exists to keep off everything else — a name the app **does** have never
     * reaches this, because every tool is registered whatever the switch says.
     */
    private fun unknown(name: String) = McpToolResult(
        text = "Tool $name not found",
        outcome = AgentActivity.Outcome.REFUSED,
    )

    companion object {

        /**
         * What the client sees on the other end, and the same in both modes: one server, whether a
         * socket or a pipe carried the handshake.
         */
        const val SERVER_NAME = "finsight"

        const val SERVER_VERSION = "1"
    }
}
