package com.neoutils.finsight.mcp

import com.neoutils.finsight.feature.mcp.api.IMcpServerSettingsRepository
import com.neoutils.finsight.feature.mcp.api.McpPermission
import com.neoutils.finsight.feature.mcp.api.McpServerSettings
import com.neoutils.finsight.mcp.contract.McpTool
import com.neoutils.finsight.mcp.contract.ToolError
import com.neoutils.finsight.mcp.contract.ToolOutcome
import com.neoutils.finsight.mcp.contract.ToolRegistry
import com.neoutils.finsight.mcp.server.CallCompletion
import com.neoutils.finsight.mcp.server.DeclaredClient
import com.neoutils.finsight.mcp.server.FINSIGHT_SERVER_INFO
import com.neoutils.finsight.mcp.server.finsightServerCapabilities
import com.neoutils.finsight.mcp.server.underCancellation
import com.neoutils.finsight.mcp.transport.BearerTokenAuth
import com.neoutils.finsight.mcp.transport.LOOPBACK_HOST
import com.neoutils.finsight.mcp.transport.MCP_ENDPOINT_PATH
import com.neoutils.finsight.mcp.transport.McpHttpTransport
import com.neoutils.finsight.mcp.transport.ToolRateLimiter
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.ServerSession
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.EmptyJsonObject
import io.modelcontextprotocol.kotlin.sdk.types.ListToolsRequest
import io.modelcontextprotocol.kotlin.sdk.types.ListToolsResult
import io.modelcontextprotocol.kotlin.sdk.types.Method
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import io.modelcontextprotocol.kotlin.sdk.types.ToolAnnotations as SdkToolAnnotations

/**
 * The stable code a call refused for want of permission carries.
 *
 * It is [com.neoutils.finsight.mcp.contract.ToolErrorCategory.PERMISSION], a class of its own,
 * which is the whole point: a consumer tells a refusal by permission from a refusal by a rule of
 * the domain **by class**, without reading a message. The rule of the domain says the operation is
 * wrong; this says the operation is fine and the server is not allowed to do it. And it is **not
 * retryable** — nothing changes until a human grants the level in Settings, so an agent told to
 * try again would only loop.
 */
const val PERMISSION_REFUSED_CODE: String = "PERMISSION_READ_ONLY"

/**
 * The JVM implementation: the only target where the MCP server actually listens.
 *
 * @see McpServerController for what it owes the rest of the app, and for why no element of the
 * Finsight interface appears anywhere in it.
 */
actual class McpServerController actual constructor(
    private val settings: IMcpServerSettingsRepository,
    private val tools: ToolRegistry,
) {

    /** Everything that exists only while the server is listening. */
    private class Listening(
        val port: Int,
        val server: Server,
        val transport: McpHttpTransport,
        var permission: McpPermission,
    )

    /**
     * Its own scope, deliberately. Tying the server's lifetime to a composition or to a window
     * would be the dependency on the app's interface the design forbids.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<McpServerState>(McpServerState.Stopped)

    actual val state: StateFlow<McpServerState> = _state.asStateFlow()

    private val mutex = Mutex()

    private var listening: Listening? = null

    private var following: Job? = null

    actual suspend fun start() {
        if (following != null) return
        apply(settings.observe().value)
        following = scope.launch {
            settings.observe().drop(1).collect { apply(it) }
        }
    }

    actual suspend fun stop() {
        following?.cancel()
        following = null
        mutex.withLock { shutdown() }
    }

    private suspend fun apply(configured: McpServerSettings) = mutex.withLock {
        val current = listening

        when {
            !configured.isEnabled -> shutdown()

            current == null -> listen(configured)

            // The address is where clients are configured to look; changing it is a restart, not
            // an adjustment.
            current.port != configured.port -> {
                shutdown()
                listen(configured)
            }

            current.permission != configured.permission -> {
                current.permission = configured.permission
                _state.value = listeningState(configured)
                // The announced listing just changed under a connected client. Telling it is the
                // reason the capability is declared at all.
                current.server.sessions.values.forEach { it.sendToolListChanged() }
            }

            else -> Unit
        }
    }

    private fun listen(configured: McpServerSettings) {
        val server = Server(
            serverInfo = FINSIGHT_SERVER_INFO,
            options = ServerOptions(capabilities = finsightServerCapabilities()),
        )
        val limiter = ToolRateLimiter()

        // Every tool is registered, announced or not. Announcing is what a well-behaved client is
        // told about; this is what holds — a client that ignores the annotations and calls a
        // write at read-only level reaches the refusal below, not a "no such tool".
        tools.tools.forEach { tool ->
            server.addTool(tool.asSdkTool()) { request ->
                execute(tool, request.arguments ?: EmptyJsonObject, limiter).asCallToolResult()
            }
        }

        val transport = McpHttpTransport(
            port = configured.port,
            server = server,
            auth = BearerTokenAuth(
                // Read on every request, so rotating the token takes effect at once, without the
                // server being stopped.
                expectedToken = { settings.observe().value.token },
                // A credential in a query string is a credential already written down elsewhere.
                // Refusing the call without revoking it would leave the leak in force.
                onTokenCompromised = { settings.rotateToken() },
            ),
            declaredClient = DeclaredClient(),
            onSession = ::announceVisibleTools,
        )

        try {
            transport.start()
        } catch (cause: IOException) {
            // The port is taken: the server does not start, the conflict is published for the
            // screen to name, and no other port is assumed in silence.
            _state.value = McpServerState.PortUnavailable(
                port = configured.port,
                reason = cause.message ?: "The port is in use by another process",
            )
            return
        }

        listening = Listening(configured.port, server, transport, configured.permission)
        _state.value = listeningState(configured)
    }

    /**
     * Makes this session answer `tools/list` with the tools the level in force admits.
     *
     * Replacing the SDK's own listing handler is what keeps hiding and refusing reading the same
     * predicate: both go through [ToolRegistry], so a tool cannot be hidden and still executable,
     * nor announced and then refused for existing.
     */
    private fun announceVisibleTools(session: ServerSession) {
        session.setRequestHandler<ListToolsRequest>(Method.Defined.ToolsList) { _, _ ->
            ListToolsResult(tools = tools.visibleTo(permissionInForce()).map { it.asSdkTool() })
        }
    }

    private fun permissionInForce(): McpPermission =
        listening?.permission ?: settings.observe().value.permission

    private suspend fun execute(
        tool: McpTool,
        arguments: JsonObject,
        limiter: ToolRateLimiter,
    ): ToolOutcome {
        // Before the tool, and therefore before any write: a refused call changes nothing.
        limiter.admit()?.let { return ToolOutcome.Failed(it) }

        if (!tools.isPermitted(tool, permissionInForce())) {
            return ToolOutcome.Failed(
                ToolError.permission(
                    code = PERMISSION_REFUSED_CODE,
                    message = "`${tool.name}` writes, and the server is at read-only permission. " +
                        "Nothing was written. This refusal is the permission level, not a rule of the domain.",
                ),
            )
        }

        return when (val completion = underCancellation { tool.execute(arguments) }) {
            is CallCompletion.Produced -> completion.value
            // Nothing further is emitted for a cancelled request. Re-raising is how the protocol
            // is told to suppress the response it would otherwise have sent.
            CallCompletion.Silenced -> throw CancellationException("Cancelled by the client")
        }
    }

    private suspend fun shutdown() {
        listening?.let {
            it.transport.stop()
            it.server.close()
        }
        listening = null
        _state.value = McpServerState.Stopped
    }

    private fun listeningState(configured: McpServerSettings) = McpServerState.Listening(
        url = "http://$LOOPBACK_HOST:${configured.port}$MCP_ENDPOINT_PATH",
        permission = configured.permission,
    )
}

/** This surface's outcome, in the shape the protocol carries a tool result in. */
internal fun ToolOutcome.asCallToolResult() = CallToolResult(
    // The structured content is the whole answer. The text block repeats it verbatim for hosts
    // that render only text, rather than paraphrasing it into a second, divergent rendering.
    content = listOf(TextContent(structuredContent.toString())),
    isError = isError,
    structuredContent = structuredContent,
)

/** This surface's tool, as the protocol announces one. */
internal fun McpTool.asSdkTool() = Tool(
    name = name,
    title = title,
    description = description,
    inputSchema = inputSchema.asToolSchema(),
    outputSchema = outputSchema.asToolSchema(),
    annotations = SdkToolAnnotations(
        title = title,
        readOnlyHint = annotations.readOnlyHint,
        destructiveHint = annotations.destructiveHint,
        idempotentHint = annotations.idempotentHint,
        openWorldHint = annotations.openWorldHint,
    ),
)

/**
 * A JSON Schema object, as the SDK's typed carrier of one.
 *
 * The contract states a schema as an ordinary `JsonObject`, which is what JSON Schema is; the SDK
 * splits out the three members it serialises by name. Nothing is invented here — an absent member
 * stays absent.
 */
private fun JsonObject.asToolSchema() = ToolSchema(
    schema = this["\$schema"]?.jsonPrimitive?.content,
    properties = this["properties"]?.jsonObject,
    required = this["required"]?.jsonArray?.map { it.jsonPrimitive.content },
    defs = this["\$defs"]?.jsonObject,
)
