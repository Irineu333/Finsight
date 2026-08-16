package com.neoutils.finsight.feature.mcp.api

/**
 * What the local MCP server is doing right now.
 *
 * There is no state for "enabled": the user's choice is a preference — [McpServerController.isEnabled] —
 * and whether the server is accepting connections is a fact about a socket. Keeping them apart is
 * what lets a screen refuse to show "up" for a server that was switched on and did not come up,
 * which is what [Failed] is for.
 */
sealed interface McpServerState {

    /** No socket is open, and no client reaches the app. */
    data object Stopped : McpServerState

    /**
     * Accepting connections on the loopback interface, at [port], with [sessions] clients holding
     * a session at this moment.
     *
     * Being up and having someone on the other side are different facts, and only the second one
     * means something may be reading the finances right now. The count lives here rather than
     * beside the state because a session cannot outlive the socket that carries it: a server that
     * is not listening has none, and there is no shape here that could claim otherwise.
     */
    data class Running(val port: Int, val sessions: Int = 0) : McpServerState

    /**
     * Switched on by the user and not listening. [cause] says why, and [port] says which port it
     * was asked for.
     *
     * The port travels with the failure because the case that actually happens is a port already
     * taken by another program, and *which* port is the whole of what the user needs in order to
     * act. Without this state the only honest answer would be [Stopped], and a server the user
     * switched on reading as "stopped" says the switch failed rather than the bind.
     */
    data class Failed(val port: Int, val cause: McpServerFailure) : McpServerState
}
