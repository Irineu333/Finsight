package com.neoutils.finsight.mcp

import com.neoutils.finsight.feature.mcp.api.McpPermissionAxis
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
import io.modelcontextprotocol.kotlin.sdk.server.ServerSession
import io.modelcontextprotocol.kotlin.sdk.server.mcpStreamableHttp
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
 * The interface this app's server is bound to, and therefore the only one anything in this app
 * dials.
 *
 * The loopback address itself, and never a hostname: `localhost` resolves through the machine's own
 * configuration, and a machine that resolves it elsewhere would put the server on an interface
 * nobody asked for. Stated once for both ends — the socket that binds it and the bridge that dials
 * it — so that "this conversation never leaves the machine" is one fact rather than two spellings
 * of it.
 */
internal const val LOOPBACK_HOST = "127.0.0.1"

/** Where the protocol is spoken on that interface, at both ends for the same reason. */
internal const val MCP_PATH = "/mcp"

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
 *
 * **What a session is comes from [McpSessionFactory], not from here.** The tools, the permission
 * filter on what is announced and on what is executed, and the instructions of the handshake are
 * the same server whether a socket or a pipe carries it (design D8). What is left here is what only
 * a listening server has: who is connected, ending them, and telling each of them the tool list
 * moved the moment the user moves a switch.
 */
internal class DesktopMcpServerController(
    private val settings: McpServerSettings,
    journal: AgentActivityJournal,
    /**
     * What the server offers. It is empty while the surface is being built; a server with no tools
     * still speaks the protocol, and answers `tools/list` with the truth about itself.
     */
    tools: List<McpTool> = emptyList(),
) : McpServerController {

    /** What every connection this transport accepts is handed to. */
    private val sessions = McpSessionFactory(
        settings = settings,
        journal = journal,
        tools = tools,
    )

    private val lifecycle = Mutex()

    private val _state = MutableStateFlow<McpServerState>(McpServerState.Stopped)

    override val state: StateFlow<McpServerState> = _state.asStateFlow()

    override val isEnabled: StateFlow<Boolean> = settings.isEnabled

    override val port: StateFlow<Int> = settings.port

    override val token: StateFlow<String?> = settings.token

    override val permissions: StateFlow<Set<McpPermissionAxis>> = settings.permissions

    override val toolCountByAxis: Map<McpPermissionAxis, Int> = McpSurface.toolCountByAxis

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
     * Held here rather than read off the SDK because [McpSessionFactory] gives each session a
     * server of its own, so no single one of them knows how many clients the app is talking to.
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

    /**
     * Moves one switch and tells whoever is listening.
     *
     * The notification goes out **after** the choice is persisted, so a client that re-lists on
     * hearing it reads the new answer and never the old one. It is sent outside the lifecycle lock
     * because writing to a client's stream is not a lifecycle operation, and holding the lock across
     * it would let a slow reader block the user switching the server off.
     */
    override suspend fun setPermission(axis: McpPermissionAxis, granted: Boolean) {
        val changed = lifecycle.withLock {
            if ((axis in settings.permissions.value) == granted) return@withLock false
            settings.setPermission(axis, granted)
            true
        }

        if (changed) announceToolListChanged()
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
                sessions.newServer(::track)
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
     * Takes a session the assembly has just opened into the count the user is shown, and out of it
     * again when the client goes.
     *
     * The count is published from both ends of the session's life, which is what makes "someone is
     * reading the finances right now" a fact about this instant rather than about the last
     * connection.
     */
    private fun track(session: ServerSession) {
        openSessions[session.sessionId] = session
        session.onClose {
            openSessions.remove(session.sessionId)
            publish()
        }
        publish()
    }

    /**
     * Tells every session in progress that what it may call has changed.
     *
     * Without it the filtering would only reach the next connection, and a user who grants a
     * capability to unblock the agent they are talking to would have to make them reconnect to be
     * believed — the notification is what `listChanged` was declared for.
     */
    private suspend fun announceToolListChanged() {
        openSessions.values.toList().forEach { session ->
            // A client whose stream has already gone is not a failure of the switch: the choice is
            // persisted either way, and the next session reads it from the handshake.
            runCatching { session.sendToolListChanged() }
        }
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
     * A port already held is the one failure the user can act on, and [BindException] is what the
     * platform raises for it — read from the chain, because the engine reports the bind through the
     * job that failed.
     *
     * The type is not that failure's alone: the JVM raises the same one when the address is refused
     * by privilege. What keeps the reading sound is the other side of the contract —
     * [McpServerController.VALID_PORTS] offers no privileged port, so the bind a permission would
     * refuse is out of reach before there is anything to classify.
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

        val ALLOWED_HOSTS = listOf("localhost", "127.0.0.1", "[::1]")

        val ALLOWED_ORIGINS = listOf("http://localhost", "http://127.0.0.1", "http://[::1]")

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
