package com.neoutils.finsight.feature.mcp.api

import kotlinx.coroutines.flow.StateFlow

/**
 * The app's control over its own MCP server: it brings it up and down, holds what the user chose
 * about it, and reports what it is doing.
 *
 * The contract names only types the whole app can name, so the process that owns the server's
 * lifetime — the desktop app — never sees the transport that implements it. Swapping the
 * transport is then a change inside one module.
 *
 * Only the desktop target has a process the user leaves running with a socket bound to it.
 * On the other targets the controller resolves to an implementation that opens nothing and
 * stays [McpServerState.Stopped].
 *
 * **Nothing here is reachable by an agent.** Switching the server on or off, moving its port and
 * minting its token are the app's side of the wire; a client able to widen its own permissions has
 * none.
 */
interface McpServerController {

    /** What the server is doing, from the moment the controller is resolved. */
    val state: StateFlow<McpServerState>

    /**
     * Whether the user has chosen to run the server — a preference that outlives the process, not
     * a report about a socket.
     *
     * It is `false` on an installation where nobody has chosen yet, so an app that gains this
     * feature in an update opens listening to nothing.
     */
    val isEnabled: StateFlow<Boolean>

    /** The port the server binds, which the user may move. Fixed and never chosen at random. */
    val port: StateFlow<Int>

    /**
     * The authorisation token a client must present, or `null` while none has been minted.
     *
     * It is minted the first time the server comes up and persisted from then on, so a client
     * configured once keeps connecting across restarts.
     */
    val token: StateFlow<String?>

    /**
     * Brings the server up **if the user has chosen to run it**, and otherwise does nothing.
     *
     * This is what the process calls when it starts: the user switches the server on once, and
     * every later launch honours that without a visit to the settings section.
     */
    suspend fun start()

    /**
     * Takes the server down and releases the port, leaving the user's choice untouched.
     *
     * This is what the process calls when it closes. It is not the user switching the server off —
     * see [setEnabled] — because an app that closed is not an app that was told to stop offering
     * the server.
     */
    suspend fun stop()

    /**
     * Records the user's choice and brings the server up or down to match it.
     *
     * Choice and socket move together here so they cannot drift: what is persisted is what runs.
     */
    suspend fun setEnabled(enabled: Boolean)

    /**
     * Moves the port and rebinds if the server was up, or was up for the trying and failed.
     *
     * This is the way out of [McpServerFailure.PORT_IN_USE]: the user resolves the clash once and
     * the client configured for the new port keeps working from then on.
     */
    suspend fun setPort(port: Int)

    /** Mints a new token. The previous one stops being accepted the moment this returns. */
    suspend fun regenerateToken()

    /**
     * Ends every session in progress, without taking the server down.
     *
     * Being switched on and having a client on the other side are different facts, and this
     * settles only the second: whoever is connected is disconnected, and the server keeps
     * listening.
     */
    suspend fun disconnectSessions()

    companion object {

        /**
         * The port the server binds unless the user moves it.
         *
         * Chosen on two grounds: outside the ranges development tooling fights over (3000, 4000,
         * 5000, 8000, 8080, 8081, 9000) and outside the ephemeral range the operating system hands
         * out on its own (49152–65535), so neither a colleague's dev server nor the kernel is
         * likely to be holding it (design D10).
         */
        const val DEFAULT_PORT: Int = 8477
    }
}
