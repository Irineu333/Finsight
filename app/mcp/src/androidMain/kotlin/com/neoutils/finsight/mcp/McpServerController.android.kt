package com.neoutils.finsight.mcp

import com.neoutils.finsight.feature.mcp.api.IMcpServerSettingsRepository
import com.neoutils.finsight.mcp.contract.ToolRegistry
import com.neoutils.finsight.mcp.prompt.PromptRegistry
import com.neoutils.finsight.mcp.resource.ResourceRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Android: inert. Nothing listens, and [state] never leaves [McpServerState.Stopped].
 *
 * The MCP server is a desktop capability, and the reason is the invariant of a single owner of
 * the database: the client reaches a process that is already running and already owns
 * `finsight.db`. On Android there is no such process to reach, so an implementation that opened
 * a socket would be opening one nobody could use, and the honest implementation is one that
 * declares it does nothing rather than one that pretends.
 *
 * The constructor takes the same arguments as the JVM one so that common code names one type.
 */
actual class McpServerController actual constructor(
    settings: IMcpServerSettingsRepository,
    tools: ToolRegistry,
    resources: ResourceRegistry,
    prompts: PromptRegistry,
) {

    private val _state = MutableStateFlow<McpServerState>(McpServerState.Stopped)

    actual val state: StateFlow<McpServerState> = _state.asStateFlow()

    actual suspend fun start(): Unit = Unit

    actual suspend fun stop(): Unit = Unit
}
