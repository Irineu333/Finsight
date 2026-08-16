package com.neoutils.finsight.feature.mcp.api

/**
 * What the local MCP server is doing right now.
 *
 * There is no state for "enabled": the user's choice is a preference, and whether the server
 * is actually accepting connections is a fact about a socket. Keeping them apart is what lets
 * a screen refuse to show "up" for a server that is enabled and did not come up.
 */
sealed interface McpServerState {

    /** No socket is open, and no client reaches the app. */
    data object Stopped : McpServerState

    /** Accepting connections on the loopback interface, at [port]. */
    data class Running(val port: Int) : McpServerState
}
