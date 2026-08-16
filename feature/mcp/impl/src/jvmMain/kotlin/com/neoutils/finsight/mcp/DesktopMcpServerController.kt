package com.neoutils.finsight.mcp

import com.neoutils.finsight.feature.mcp.api.McpServerController
import com.neoutils.finsight.feature.mcp.api.McpServerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The desktop controller — the seat of the real server, because the JVM desktop target is the
 * only one whose process owns a socket.
 *
 * It holds the observable state and nothing else: the transport that binds the loopback
 * interface and serves the protocol is task group 6, and lands in this class. Until then
 * [start] opens no socket, and [state] reports that plainly rather than anticipating it.
 */
internal class DesktopMcpServerController : McpServerController {

    private val _state = MutableStateFlow<McpServerState>(McpServerState.Stopped)

    override val state: StateFlow<McpServerState> = _state.asStateFlow()

    override suspend fun start() = Unit

    override suspend fun stop() = Unit
}
