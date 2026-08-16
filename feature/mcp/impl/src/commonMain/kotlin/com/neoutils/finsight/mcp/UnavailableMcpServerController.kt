package com.neoutils.finsight.mcp

import com.neoutils.finsight.feature.mcp.api.McpServerController
import com.neoutils.finsight.feature.mcp.api.McpServerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The controller of a platform that has no server: [start] opens nothing, and [state] never
 * leaves [McpServerState.Stopped].
 *
 * A local MCP server is reached over a socket by a client on the same machine. Android and
 * iOS have neither that client nor a process the user leaves listening, so the honest
 * implementation there is the one that never claims to be up.
 */
internal class UnavailableMcpServerController : McpServerController {

    override val state: StateFlow<McpServerState> = MutableStateFlow(McpServerState.Stopped)

    override suspend fun start() = Unit

    override suspend fun stop() = Unit
}
