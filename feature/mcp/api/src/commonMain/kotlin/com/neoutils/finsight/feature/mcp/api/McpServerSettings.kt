package com.neoutils.finsight.feature.mcp.api

import kotlinx.coroutines.flow.StateFlow

/**
 * How much of the app an agent reaches through the MCP server.
 *
 * **A decision separate from the toggle, and it starts at [READ_ONLY].** Letting an agent
 * read the user's finances and letting it write to the ledger are risks of very different
 * sizes, so they are two keys and not one: enabling the server never, by itself, grants a
 * write. Reading already delivers most of the value, which is why the smaller of the two
 * is the one granted first.
 */
enum class McpPermission {
    /**
     * Reads only. Write tools are not announced in the tool listing **and** are refused at
     * execution, naming the permission as the reason — hiding is for the well-behaved
     * client, refusing is what holds.
     */
    READ_ONLY,

    /** Reads and writes. Every write still goes through a domain use case. */
    READ_WRITE,
}

/**
 * The whole persisted state of the MCP server, as the user configured it.
 *
 * The four fields are independent on purpose; none is derived from another, and none is
 * reset as a side effect of changing another.
 */
data class McpServerSettings(
    /**
     * Whether the MCP server exists.
     *
     * **It is off by default** — on a fresh install and on an upgrade of an existing one
     * alike. The capability opens a door with power over the ledger, and nobody gets it
     * without having asked for it.
     *
     * This flag means **always and only** "the MCP server exists". How long the process
     * lives — while the window is open, while the user is logged in, from boot — is the
     * business of other keys, which do not exist in this delivery. Keeping them separate
     * from the start is what stops a future step from redefining a consent already given:
     * the same switch would silently change its promise without changing its name.
     */
    val isEnabled: Boolean,
    /** The level in force. Independent of [isEnabled], and starts at [McpPermission.READ_ONLY]. */
    val permission: McpPermission,
    /**
     * The port the server listens on.
     *
     * **Persisted and reused on every start, never drawn at random.** The configuration a
     * user pastes into an MCP client contains the URL, so an address that changed between
     * runs would break every configured client — the "working state that does not work"
     * this screen exists to avoid. When the persisted port is taken by another process the
     * server fails to start and shows the conflict; it does not quietly pick another one,
     * which is the same breakage, only rarer and therefore harder to diagnose.
     */
    val port: Int,
    /**
     * The bearer credential every request must present.
     *
     * It authenticates the request, not the caller: it is the same token for every client.
     * It is never written to a log, to telemetry or to the activity journal, and is not
     * shown in clear by default.
     */
    val token: String,
)

/**
 * The single owner of [McpServerSettings] — the one place the configuration is read from
 * and written to.
 */
interface IMcpServerSettingsRepository {

    /** The state in force, emitting on every change so a screen never has to be reopened. */
    fun observe(): StateFlow<McpServerSettings>

    /**
     * Turns the server on or off.
     *
     * **Turning it off does not rotate the token, and does not touch the permission
     * level.** If switching off for a minute invalidated every configured client, the user
     * would learn never to switch off, and the safety switch would become the switch nobody
     * touches. Re-enabling brings back a client that was already configured, untouched.
     */
    suspend fun setEnabled(isEnabled: Boolean)

    /**
     * Changes the permission level. Independent of [setEnabled] in both directions: the
     * level survives the server being turned off and on.
     */
    suspend fun setPermission(permission: McpPermission)

    /**
     * Changes the port the server listens on — the deliberate choice offered when the
     * persisted one is taken. The new value is persisted and reused from then on.
     */
    suspend fun setPort(port: Int)

    /**
     * Revokes the current token and persists a new one, returning it.
     *
     * **This is the only thing that changes the token.** Rotation is the gesture of
     * revocation, so it is the explicit button and nothing else — never a side effect of
     * disabling, of changing the level or of restarting. The previous token stops being
     * accepted immediately, and the new one takes effect without the server being stopped.
     */
    suspend fun rotateToken(): String
}
