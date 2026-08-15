package com.neoutils.finsight.mcp

import com.neoutils.finsight.feature.mcp.api.IMcpServerSettingsRepository
import com.neoutils.finsight.feature.mcp.api.McpServerState
import com.neoutils.finsight.mcp.contract.ToolRegistry
import com.neoutils.finsight.mcp.server.DeclaredClientName
import com.neoutils.finsight.mcp.prompt.PromptRegistry
import com.neoutils.finsight.mcp.resource.ResourceRegistry
import kotlinx.coroutines.flow.StateFlow

/**
 * Owns the lifetime of the MCP server: it starts, it stops, and it follows the configuration —
 * **with nothing of the Finsight interface involved**.
 *
 * That absence is the requirement, not a side effect. This is not started from a composition
 * scope, it reads no window state, and no tool it serves needs anyone looking at the app's screen
 * to finish. A tool completes with the window minimised, and consent is the *policy* — what
 * Settings permits — never a modal. The protocol's own way of asking the user something happens
 * in the **client's** interface, and that stays available.
 *
 * It serves the three primitives of the protocol and not only tools: the orientation
 * documents are published as **resources**, so a client can attach them before the model
 * decides anything, and the flows the user invokes by name are **prompts**, which are text
 * and therefore cannot decide which rule applies.
 *
 * It reacts to two keys of [IMcpServerSettingsRepository], which are independent:
 * - **the toggle**, which decides whether a socket exists at all;
 * - **the permission level**, which decides which tools are announced. Changing it while a client
 *   is connected emits the tool list change notification, so the client stops seeing a listing
 *   that is no longer the truth.
 *
 * `expect` because the type is named from common code, and **actual on the JVM only**. Android and
 * iOS get inert implementations that never listen: this server exists on the desktop, where the
 * process the user already has open is the one that owns the database.
 */
expect class McpServerController(
    settings: IMcpServerSettingsRepository,
    tools: ToolRegistry,
    resources: ResourceRegistry,
    prompts: PromptRegistry,
    declaredClient: DeclaredClientName,
) {

    /** What the server is doing, for the screen to render. Emits on every change. */
    val state: StateFlow<McpServerState>

    /**
     * Applies the configuration in force and starts following it. Idempotent: calling it on a
     * controller that is already following does nothing.
     */
    suspend fun start()

    /**
     * Stops following the configuration and closes the socket. Afterwards nothing is listening.
     *
     * It does not touch the token or the level: those are the user's, and a stop that revoked
     * them would teach the user never to stop.
     */
    suspend fun stop()
}
