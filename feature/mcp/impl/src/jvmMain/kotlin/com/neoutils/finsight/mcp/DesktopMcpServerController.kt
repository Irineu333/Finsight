package com.neoutils.finsight.mcp

import com.neoutils.finsight.feature.mcp.api.AgentActivity
import com.neoutils.finsight.feature.mcp.api.McpServerController
import com.neoutils.finsight.feature.mcp.api.McpServerFailure
import com.neoutils.finsight.feature.mcp.api.McpServerState
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.header
import io.ktor.server.request.path
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.ServerSession
import io.modelcontextprotocol.kotlin.sdk.server.mcpStreamableHttp
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.BindException
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * The desktop controller — the seat of the real server, because the JVM desktop target is the only
 * one whose process owns a socket.
 *
 * **The perimeter is this machine, and it has three layers.** The socket is bound to the loopback
 * address, so a connection from another host is never established. Every request must present the
 * token, checked before routing and therefore before anything the server could execute. And `Host`
 * and `Origin` are validated against loopback, which is the defence against the attack a local
 * server actually faces: a web page open in the user's own browser reaching `127.0.0.1`, directly
 * or through DNS rebinding (design D11).
 *
 * **A bind that fails is said out loud.** [McpServerState.Running] is published only after the
 * socket is bound, and a failure lands in [McpServerState.Failed] carrying the port — never a quiet
 * move to a free one, which would leave a configured client pointing at nothing while the app
 * reported success (design D10).
 */
internal class DesktopMcpServerController(
    private val settings: McpServerSettings,
    private val journal: AgentActivityJournal,
    /**
     * What the server offers. It is empty while the surface is being built; a server with no tools
     * still speaks the protocol, and answers `tools/list` with the truth about itself.
     */
    private val tools: List<McpTool> = emptyList(),
) : McpServerController {

    private val lifecycle = Mutex()

    private val _state = MutableStateFlow<McpServerState>(McpServerState.Stopped)

    override val state: StateFlow<McpServerState> = _state.asStateFlow()

    override val isEnabled: StateFlow<Boolean> = settings.isEnabled

    override val port: StateFlow<Int> = settings.port

    override val token: StateFlow<String?> = settings.token

    /**
     * The scope the engine's jobs hang from, owned here rather than left global.
     *
     * A bind that fails takes the engine's job down with it, and [bringUp] has already turned that
     * into [McpServerState.Failed] by the time it lands here — so what this absorbs is a second
     * copy of something the app is already saying, and the alternative is a stack trace printed
     * where a desktop user will never see it. Nothing a request does arrives here: the call
     * pipeline answers its own failures.
     */
    private val serverScope = CoroutineScope(
        SupervisorJob() + CoroutineExceptionHandler { _, _ -> },
    )

    /** Written under [lifecycle], and read from the session callbacks, which run on other threads. */
    @Volatile
    private var engine: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null

    @Volatile
    private var boundPort: Int = McpServerController.DEFAULT_PORT

    /**
     * The sessions in progress, by the identity the protocol gave them.
     *
     * Held here rather than read off the SDK because each session gets a [Server] of its own, so no
     * single one of them knows how many clients the app is talking to.
     */
    private val openSessions = ConcurrentHashMap<String, ServerSession>()

    override suspend fun start(): Unit = lifecycle.withLock {
        if (!settings.isEnabled.value) return@withLock
        bringUp()
    }

    override suspend fun stop(): Unit = lifecycle.withLock {
        takeDown()
    }

    override suspend fun setEnabled(enabled: Boolean): Unit = lifecycle.withLock {
        settings.setEnabled(enabled)
        if (enabled) bringUp() else takeDown()
    }

    override suspend fun setPort(port: Int): Unit = lifecycle.withLock {
        val wasUp = _state.value != McpServerState.Stopped
        settings.setPort(port)
        if (!wasUp) return@withLock
        takeDown()
        if (settings.isEnabled.value) bringUp()
    }

    override suspend fun regenerateToken(): Unit = lifecycle.withLock {
        settings.regenerateToken()
        // The token is read once, when the socket is bound, so the old one keeps being accepted
        // until the server is rebound with the new one. Whoever was connected is disconnected by
        // the rebind, which is what "the previous token stops being accepted" has to mean.
        if (_state.value == McpServerState.Stopped) return@withLock
        takeDown()
        if (settings.isEnabled.value) bringUp()
    }

    override suspend fun disconnectSessions(): Unit = lifecycle.withLock {
        closeSessions()
        publish()
    }

    private suspend fun bringUp() {
        if (engine != null) return

        val port = settings.port.value
        val token = settings.requireToken()

        val server = serverScope.embeddedServer(CIO, host = LOOPBACK_HOST, port = port) {
            requireToken(token)
            mcpStreamableHttp(
                path = MCP_PATH,
                enableDnsRebindingProtection = true,
                allowedHosts = ALLOWED_HOSTS,
                allowedOrigins = ALLOWED_ORIGINS,
            ) {
                newServer()
            }
        }

        val failure = try {
            // Suspends until the socket is bound, and throws when the bind fails — which is what
            // lets the state below be a fact rather than an intention.
            server.startSuspend(wait = false)
            null
        } catch (cause: Throwable) {
            // A bind that fails arrives as the cancellation of the engine's own job, so the usual
            // "rethrow every CancellationException" would swallow exactly the failure this exists
            // to report. This asks the only question that separates the two: were *we* cancelled?
            currentCoroutineContext().ensureActive()
            cause
        }

        if (failure != null) {
            runCatching { server.stopSuspend(gracePeriodMillis = 0, timeoutMillis = STOP_TIMEOUT_MILLIS) }
            _state.value = McpServerState.Failed(port = port, cause = failure.classify())
            return
        }

        engine = server
        boundPort = port
        publish()
    }

    private suspend fun takeDown() {
        closeSessions()
        engine?.let { server ->
            runCatching {
                server.stopSuspend(
                    gracePeriodMillis = STOP_GRACE_MILLIS,
                    timeoutMillis = STOP_TIMEOUT_MILLIS,
                )
            }
        }
        engine = null
        _state.value = McpServerState.Stopped
    }

    private suspend fun closeSessions() {
        openSessions.values.toList().forEach { session -> runCatching { session.close() } }
        openSessions.clear()
    }

    /**
     * One [Server] per client session, which is the shape the transport asks for: the factory below
     * is called once for each connection that arrives without a session of its own.
     */
    private fun newServer(): Server {
        val server = Server(
            serverInfo = Implementation(name = SERVER_NAME, version = SERVER_VERSION),
            options = ServerOptions(
                capabilities = ServerCapabilities(
                    // The permission axes decide which tools are announced, so the list changes
                    // while a client is connected and the client has to be told (design D5).
                    tools = ServerCapabilities.Tools(listChanged = true),
                ),
            ),
        )

        tools.forEach { tool -> server.register(tool) }

        server.onConnect {
            // The only moment the session exists and is reachable: `onConnect` runs at the end of
            // the SDK's own session setup, with the session already registered.
            server.sessions.values.forEach { session ->
                if (openSessions.putIfAbsent(session.sessionId, session) == null) {
                    session.onClose {
                        openSessions.remove(session.sessionId)
                        publish()
                    }
                }
            }
            publish()
        }

        return server
    }

    private fun Server.register(tool: McpTool) = addTool(
        name = tool.name,
        description = tool.description,
        inputSchema = tool.inputSchema,
    ) { request ->
        val result = journal.execute(tool, request.arguments)
        CallToolResult(
            content = listOf(TextContent(result.text)),
            // A refusal is the tool's own answer and not a protocol fault: the agent has to read it
            // and change course, which it cannot do with a transport-level error.
            isError = result.outcome == AgentActivity.Outcome.REFUSED,
        )
    }

    private fun publish() {
        if (engine == null) return
        _state.value = McpServerState.Running(port = boundPort, sessions = openSessions.size)
    }

    /**
     * Refuses anything that does not present the token, before routing and therefore before the
     * transport, the session or any tool exists.
     *
     * It sits on the application and not on the route because the SDK's own DSL opens its routing
     * block internally, leaving no route to wrap.
     */
    private fun Application.requireToken(token: String) {
        val expected = "Bearer $token".encodeToByteArray()
        intercept(ApplicationCallPipeline.Plugins) {
            if (!call.request.path().startsWith(MCP_PATH)) return@intercept
            val presented = call.request.header(HttpHeaders.Authorization)?.encodeToByteArray()
            if (presented == null || !MessageDigest.isEqual(presented, expected)) {
                call.response.header(HttpHeaders.WWWAuthenticate, "Bearer")
                call.respondText(
                    text = UNAUTHORIZED_BODY,
                    contentType = ContentType.Application.Json,
                    status = HttpStatusCode.Unauthorized,
                )
                finish()
            }
        }
    }

    /**
     * A port already held is the one failure the user can act on, and [BindException] is the
     * platform's own typed answer to that question — read from the chain, because the engine
     * reports the bind through the job that failed.
     */
    private fun Throwable.classify(): McpServerFailure {
        var cause: Throwable? = this
        while (cause != null) {
            if (cause is BindException) return McpServerFailure.PORT_IN_USE
            cause = cause.cause
        }
        return McpServerFailure.UNAVAILABLE
    }

    private companion object {

        /**
         * The loopback address itself, and never a hostname: `localhost` resolves through the
         * machine's own configuration, and a machine that resolves it elsewhere would put the
         * server on an interface nobody asked for.
         */
        const val LOOPBACK_HOST = "127.0.0.1"

        const val MCP_PATH = "/mcp"

        val ALLOWED_HOSTS = listOf("localhost", "127.0.0.1", "[::1]")

        val ALLOWED_ORIGINS = listOf("http://localhost", "http://127.0.0.1", "http://[::1]")

        const val SERVER_NAME = "finsight"

        const val SERVER_VERSION = "1"

        const val STOP_GRACE_MILLIS = 250L

        const val STOP_TIMEOUT_MILLIS = 2_000L

        /**
         * The refusal in the shape the protocol speaks, so a client reads a reason instead of an
         * empty body. `-32000` is the SDK's own code for a connection the server will not carry.
         */
        const val UNAUTHORIZED_BODY =
            """{"jsonrpc":"2.0","id":null,"error":{"code":-32000,"message":"Unauthorized: a valid token is required"}}"""
    }
}
