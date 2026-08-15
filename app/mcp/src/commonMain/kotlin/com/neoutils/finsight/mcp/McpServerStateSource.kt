package com.neoutils.finsight.mcp

import com.neoutils.finsight.feature.mcp.api.IMcpServerStateSource
import com.neoutils.finsight.feature.mcp.api.McpServerState
import kotlinx.coroutines.flow.StateFlow

/**
 * The controller, seen as the question the configuration screen asks.
 *
 * The screen may not name [McpServerController] — it lives in an app module a feature cannot
 * depend on — and the controller may not name the screen, which is the whole point of it holding
 * no element of the interface. This adapter is where the two are both visible, and it adds
 * nothing: the state it publishes is the controller's own.
 */
class McpServerStateSource(
    private val controller: McpServerController,
) : IMcpServerStateSource {

    override val state: StateFlow<McpServerState> get() = controller.state
}
