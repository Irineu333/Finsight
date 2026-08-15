package com.neoutils.finsight.feature.mcp.api

import kotlinx.coroutines.flow.StateFlow

/**
 * What the MCP server is actually doing — the three states the configuration screen distinguishes,
 * and it distinguishes them because presenting any two of them alike would tell the user the
 * capability is in a state it is not.
 *
 * It lives in the `api` because the screen renders it and the server produces it, and the two are
 * in different modules that cannot see each other: `:app:mcp` owns the socket and depends on this
 * contract; `feature:mcp:impl` owns the screen and depends on the same one. A second shape on
 * either side would be a second answer to "is it listening".
 */
sealed interface McpServerState {

    /** Nothing is listening. No socket exists — not an idle one, not a closed one. */
    data object Stopped : McpServerState

    /**
     * Listening, and reachable at [url] with the level [permission] in force.
     *
     * [url] is the whole endpoint, because the endpoint is what a client's configuration holds;
     * handing the screen a host and a port to assemble would put the assembling rule in two
     * places.
     *
     * [protocolRevision] is the revision of the Model Context Protocol the server that is
     * **actually listening** speaks. It travels with the state, and is not a constant the screen
     * writes down, for the same reason [url] does: the running server is the only thing that
     * knows, and a client that speaks another revision fails to connect with nothing else on the
     * screen explaining the failure.
     */
    data class Listening(
        val url: String,
        val permission: McpPermission,
        val protocolRevision: String,
    ) : McpServerState

    /**
     * The persisted port is taken by another process, so **the server did not start**.
     *
     * A state of its own on purpose. Presented as stopped, the user would think the switch is off;
     * presented as listening, they would think it works. Neither is true, and no other port is
     * taken silently — an address that moves breaks every client that pasted the old one.
     */
    data class PortUnavailable(val port: Int, val reason: String) : McpServerState
}

/**
 * Where the configuration screen reads [McpServerState] from.
 *
 * The screen asks a question only the process that owns the socket can answer — *is it listening,
 * and where* — and the module that owns the socket is not one a feature may name. This is the
 * port: the server's controller is adapted onto it where both are visible, and the screen depends
 * on the question rather than on the answerer.
 */
interface IMcpServerStateSource {

    /** The state in force, emitting on every change so the screen never has to be reopened. */
    val state: StateFlow<McpServerState>
}
