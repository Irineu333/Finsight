package com.neoutils.finsight.feature.mcp.api

import kotlinx.coroutines.flow.StateFlow

/**
 * Brings the local MCP server up and down, and reports what it is doing.
 *
 * The contract names only types the whole app can name, so the process that owns the server's
 * lifetime — the desktop app — never sees the transport that implements it. Swapping the
 * transport is then a change inside one module.
 *
 * Only the desktop target has a process the user leaves running with a socket bound to it.
 * On the other targets the controller resolves to an implementation that opens nothing and
 * stays [McpServerState.Stopped].
 */
interface McpServerController {

    /** What the server is doing, from the moment the controller is resolved. */
    val state: StateFlow<McpServerState>

    /** Brings the server up. Starting a server that is already up changes nothing. */
    suspend fun start()

    /** Takes the server down and releases the port. Stopping a stopped server changes nothing. */
    suspend fun stop()
}
