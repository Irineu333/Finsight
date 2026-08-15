package com.neoutils.finsight.database.repository

import com.neoutils.finsight.feature.mcp.api.IMcpServerSettingsRepository
import com.neoutils.finsight.feature.mcp.api.McpPermission
import com.neoutils.finsight.feature.mcp.api.McpServerSettings
import com.neoutils.finsight.security.generateMcpToken
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The MCP server's configuration over `multiplatform-settings` — the same mechanism
 * `RateSyncStateRepository` and `BaseCurrencyRepository` use, with keys of its own.
 *
 * Four scalars and no table: this is a preference, not a model. Each key is independent, and
 * **no write here has a side effect on another key** — that independence is the whole point of
 * the shape (`McpServerSettings`). Turning the server off leaves the token and the permission
 * level exactly as they were, and rotating the token leaves the switch where it was.
 *
 * The state is exposed as a `StateFlow` so the settings screen follows it without an event and
 * without being reopened.
 *
 * **The token is written to no log.** It is read from here and rendered hidden by the screen;
 * it appears in no other surface, and least of all in the activity journal.
 */
class McpServerSettingsRepository(
    private val settings: Settings,
) : IMcpServerSettingsRepository {

    private val state = MutableStateFlow(read())

    override fun observe(): StateFlow<McpServerSettings> = state

    override suspend fun setEnabled(isEnabled: Boolean) {
        settings.putBoolean(KEY_ENABLED, isEnabled)
        state.value = state.value.copy(isEnabled = isEnabled)
    }

    override suspend fun setPermission(permission: McpPermission) {
        settings.putString(KEY_PERMISSION, permission.name)
        state.value = state.value.copy(permission = permission)
    }

    override suspend fun setPort(port: Int) {
        settings.putInt(KEY_PORT, port)
        state.value = state.value.copy(port = port)
    }

    override suspend fun rotateToken(): String {
        val token = generateMcpToken()
        settings.putString(KEY_TOKEN, token)
        state.value = state.value.copy(token = token)
        return token
    }

    /**
     * The state as persisted, **completing what is missing and persisting the completion**.
     *
     * The port and the token are drawn once, on the first run, and stored right there. Drawing
     * either of them again on each start would break every configured client, since what the
     * user pasted into one contains both: an address that moves and a credential that expires
     * are the same failure, and it is discovered only when the client stops working.
     *
     * A permission that cannot be read falls back to the smaller of the two levels. That is
     * the same answer a fresh install gets, and it is the only safe direction to guess in.
     */
    private fun read() = McpServerSettings(
        isEnabled = settings.getBoolean(KEY_ENABLED, defaultValue = false),
        permission = settings.getStringOrNull(KEY_PERMISSION)
            ?.let { stored -> McpPermission.entries.firstOrNull { it.name == stored } }
            ?: McpPermission.READ_ONLY,
        port = settings.getIntOrNull(KEY_PORT)
            ?: DEFAULT_PORT.also { settings.putInt(KEY_PORT, it) },
        token = settings.getStringOrNull(KEY_TOKEN)
            ?: generateMcpToken().also { settings.putString(KEY_TOKEN, it) },
    )

    companion object {
        private const val KEY_ENABLED = "mcp_server_enabled"
        private const val KEY_PERMISSION = "mcp_server_permission"
        private const val KEY_PORT = "mcp_server_port"
        private const val KEY_TOKEN = "mcp_server_token"

        /**
         * The port a fresh install listens on — a *first* value, not a fixed one: it is
         * persisted on the first read and the user can change it when something else already
         * holds it.
         *
         * It is above 1024, so no privilege is needed, and below 49152, so it sits outside the
         * range the operating system hands out to outgoing connections — a default inside that
         * range would be taken from under the server at random and would look like an
         * intermittent defect rather than a conflict.
         */
        const val DEFAULT_PORT = 8765
    }
}
