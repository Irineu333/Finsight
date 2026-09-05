package com.neoutils.finsight.mcp

import com.neoutils.finsight.feature.mcp.api.McpLaunchCommand
import com.neoutils.finsight.feature.mcp.api.McpPermissionAxis
import com.neoutils.finsight.feature.mcp.api.McpServerController
import com.neoutils.finsight.feature.mcp.api.McpServerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The controller of a platform that has no server: nothing here opens a socket, [state] never
 * leaves [McpServerState.Stopped], and there is no choice to persist because there is nothing the
 * choice could turn on.
 *
 * A local MCP server is reached over a socket by a client on the same machine. Android and
 * iOS have neither that client nor a process the user leaves listening, so the honest
 * implementation there is the one that never claims to be up.
 */
internal class UnavailableMcpServerController : McpServerController {

    override val state: StateFlow<McpServerState> = MutableStateFlow(McpServerState.Stopped)

    override val isEnabled: StateFlow<Boolean> = MutableStateFlow(false)

    override val port: StateFlow<Int> = MutableStateFlow(McpServerController.DEFAULT_PORT)

    override val token: StateFlow<String?> = MutableStateFlow(null)

    /**
     * Nothing is granted, because there is nothing to grant it to. Not even reading: a permission
     * here would describe an agent that has no way to arrive.
     */
    override val permissions: StateFlow<Set<McpPermissionAxis>> = MutableStateFlow(emptySet())

    override val toolCountByAxis: Map<McpPermissionAxis, Int> = emptyMap()

    /**
     * There is no command, because there is no process a client could start: here the app is
     * launched by the system on the user's tap, and its standard streams go to a log.
     */
    override val launchCommand: McpLaunchCommand? = null

    override suspend fun start() = Unit

    override suspend fun stop() = Unit

    override suspend fun setEnabled(enabled: Boolean) = Unit

    override suspend fun setPort(port: Int) = Unit

    override suspend fun setPermission(axis: McpPermissionAxis, granted: Boolean) = Unit

    override suspend fun regenerateToken() = Unit

    override suspend fun disconnectSessions() = Unit
}
