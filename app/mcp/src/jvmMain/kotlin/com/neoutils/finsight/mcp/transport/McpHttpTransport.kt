package com.neoutils.finsight.mcp.transport

import com.neoutils.finsight.mcp.server.DeclaredClient
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.header
import io.ktor.server.request.httpMethod
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.sse.SSE
import io.ktor.server.sse.sse
import io.modelcontextprotocol.kotlin.sdk.server.DnsRebindingProtection
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerSession
import io.modelcontextprotocol.kotlin.sdk.server.StreamableHttpServerTransport
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCError
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import io.modelcontextprotocol.kotlin.sdk.types.RPCError
import io.modelcontextprotocol.kotlin.sdk.types.SUPPORTED_PROTOCOL_VERSIONS
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket

/**
 * The only address this server is ever bound to. Not a default and not configurable: a server
 * that writes to the ledger has no business being reachable from another machine.
 */
const val LOOPBACK_HOST: String = "127.0.0.1"

/**
 * The **single** endpoint path. `POST` carries the requests, `GET` opens the stream of
 * notifications the server initiates; there is no third path and no second verb, which is what
 * makes "what is reachable on this port" a question with one answer.
 */
const val MCP_ENDPOINT_PATH: String = "/mcp"

/**
 * Where the protected resource metadata document is served, per RFC 9728. It is the target the
 * `401` challenge points at, and it is reachable **without** a credential — a document describing
 * what credential to bring, behind the credential, would say nothing to the client that needs it.
 */
const val PROTECTED_RESOURCE_METADATA_PATH: String = "/.well-known/oauth-protected-resource"

/** The header every request carries the negotiated revision in. */
internal const val PROTOCOL_VERSION_HEADER: String = "MCP-Protocol-Version"

/** Hostnames a request's `Host` header may name. Loopback, in the three ways it is spelled. */
private val ALLOWED_HOSTS = listOf("localhost", "127.0.0.1", "[::1]")

/** Origins a browser-borne request may declare. Same three, carrying a scheme so they parse. */
private val ALLOWED_ORIGINS = listOf("http://localhost", "http://127.0.0.1", "http://[::1]")

/**
 * The HTTP face of the MCP server: Streamable HTTP on loopback, one path, no session.
 *
 * **Bound exclusively to [LOOPBACK_HOST].** Never to every interface, not even behind a
 * configuration flag — an address that can be widened by a setting is an address that will be.
 *
 * **`Origin` is validated on every request, before anything else happens.** A request whose
 * `Origin` is present and unrecognised is answered `403` in the pipeline, before any tool runs
 * and before a single row is read from the database. The revision requires it as a `MUST`, and
 * the reason is concrete: without it a web page the user has open reaches this server by DNS
 * rebinding, and this server writes to the ledger. The check is the SDK's own
 * `DnsRebindingProtection`, installed first on the route so it runs first; requests with no
 * `Origin` at all are admitted, because a non-browser client cannot be rebound.
 *
 * **No session is assigned.** The revision permits sessions and this server declines them: a
 * single-user local server has no conversation state to keep, and a session identifier would be
 * one more secret to leak. The SDK's session generator is switched off, so no `Mcp-Session-Id`
 * is ever emitted and no request has to carry one.
 *
 * **The protocol version header is required, and an unsupported value is `400`.** With one
 * exception the revision itself defines: the `initialize` request cannot carry a negotiated
 * version, because negotiating it is what that request is for. This server reads that exception
 * literally and uses it as a signal — *a `POST` without the header is an initialisation*, and it
 * is what makes a client able to reconnect. Every other request, `GET` included, is refused with
 * `400` when the header is missing or names a revision outside
 * [SUPPORTED_PROTOCOL_VERSIONS]. Requiring the header on `initialize` too would have refused
 * every conforming client, which is not a reading of "required" worth having.
 *
 * **One MCP connection is shared by every HTTP exchange.** It has to be: in this revision a
 * client cancels a call by sending `notifications/cancelled` as a *separate* request, and a
 * cancellation can only find the call it names if both landed on the same connection. A
 * connection per exchange — which is what the SDK's stateless helper builds — would silently drop
 * every cancellation. The shared connection is torn down and rebuilt on the next initialisation,
 * and at no other time; in particular **an HTTP exchange ending never closes it**, which is what
 * keeps losing the connection from behaving like a cancellation.
 */
class McpHttpTransport(
    private val port: Int,
    private val server: Server,
    private val auth: BearerTokenAuth,
    private val declaredClient: DeclaredClient,
    private val onSession: (ServerSession) -> Unit = {},
) {

    /** The transport and the MCP session every exchange shares. See the class note. */
    private class Connection(
        val transport: StreamableHttpServerTransport,
        val session: ServerSession,
    )

    private val mutex = Mutex()

    private var connection: Connection? = null

    private var engine: EmbeddedServer<*, *>? = null

    /** The URL of the metadata document, as the challenge names it. */
    private val resourceMetadataUrl = "http://$LOOPBACK_HOST:$port$PROTECTED_RESOURCE_METADATA_PATH"

    /**
     * Binds the port and starts listening.
     *
     * @throws java.io.IOException when the port is taken. The caller publishes the conflict; this
     * class never falls back to another port, because an address that moves breaks every client
     * that pasted the old one — the rarer failure, and therefore the harder one to diagnose.
     */
    fun start() {
        check(engine == null) { "This transport is already listening on $LOOPBACK_HOST:$port" }

        // Probing first turns a port conflict into a plain exception here, rather than into an
        // engine that reports itself started while its accept loop has already died.
        ServerSocket().use { probe ->
            probe.reuseAddress = false
            probe.bind(InetSocketAddress(InetAddress.getByName(LOOPBACK_HOST), port))
        }

        engine = embeddedServer(CIO, host = LOOPBACK_HOST, port = port) {
            install(ContentNegotiation) { json(McpJson) }
            install(SSE)
            routing {
                mcpEndpoint()
                protectedResourceMetadata()
            }
        }.also { it.start(wait = false) }
    }

    /** Stops listening and closes the shared connection. Afterwards no socket is listening. */
    suspend fun stop() {
        connection?.let { runCatching { it.session.close() } }
        connection = null
        engine?.stop(gracePeriodMillis = 0, timeoutMillis = STOP_TIMEOUT_MILLIS)
        engine = null
    }

    private fun Route.mcpEndpoint() = route(MCP_ENDPOINT_PATH) {
        // Installed before anything else on this route so that an unrecognised Origin is
        // answered 403 ahead of the credential check, ahead of any handler, and ahead of any
        // read of the user's data.
        install(DnsRebindingProtection) {
            allowedHosts = ALLOWED_HOSTS
            allowedOrigins = ALLOWED_ORIGINS
        }

        // Installed second, so it runs after the Origin check: a request from an unrecognised
        // Origin is refused without its credential ever being examined.
        install(
            createRouteScopedPlugin("McpRequestGuards") {
                onCall { call ->
                    if (!call.admitProtocolVersion()) return@onCall
                    call.admitCredential()
                }
            },
        )

        post {
            val current = if (call.isInitialisation()) rebuildConnection() else connection
            if (current == null) {
                call.rejectJsonRpc(
                    HttpStatusCode.BadRequest,
                    RPCError.ErrorCode.INVALID_REQUEST,
                    "Bad Request: the connection has not been initialised",
                )
                return@post
            }
            current.transport.handlePostRequest(session = null, call = call)
        }

        sse {
            // The interceptor already refused a GET arriving before initialisation, so this is
            // defensive: an unusable stream is closed rather than left open saying nothing.
            val current = connection ?: return@sse
            current.transport.handleGetRequest(session = this, call = call)
        }
    }

    /**
     * The document the `401` challenge points at, served without a credential.
     *
     * It is deliberately small. This server is not an OAuth 2.1 resource server and does not
     * pretend to be one: it names itself, says the credential travels in the header, and lists no
     * authorization server, because there is none. See [BearerTokenAuth] for why.
     */
    private fun Route.protectedResourceMetadata() = get(PROTECTED_RESOURCE_METADATA_PATH) {
        call.respondText(
            text = """
                {
                  "resource": "http://$LOOPBACK_HOST:$port$MCP_ENDPOINT_PATH",
                  "resource_name": "Finsight MCP",
                  "bearer_methods_supported": ["header"]
                }
            """.trimIndent(),
            contentType = ContentType.Application.Json,
        )
    }

    /** A `POST` without the version header is the `initialize` — see the class note. */
    private fun ApplicationCall.isInitialisation(): Boolean =
        request.httpMethod == HttpMethod.Post && request.header(PROTOCOL_VERSION_HEADER) == null

    private suspend fun ApplicationCall.admitProtocolVersion(): Boolean {
        val declared = request.header(PROTOCOL_VERSION_HEADER)

        if (declared == null) {
            if (isInitialisation()) return true
            rejectJsonRpc(
                HttpStatusCode.BadRequest,
                RPCError.ErrorCode.INVALID_REQUEST,
                "Bad Request: the $PROTOCOL_VERSION_HEADER header is required",
            )
            return false
        }

        if (declared !in SUPPORTED_PROTOCOL_VERSIONS) {
            rejectJsonRpc(
                HttpStatusCode.BadRequest,
                RPCError.ErrorCode.INVALID_REQUEST,
                "Bad Request: unsupported protocol version $declared " +
                    "(supported: ${SUPPORTED_PROTOCOL_VERSIONS.joinToString(", ")})",
            )
            return false
        }

        if (request.httpMethod == HttpMethod.Get && connection == null) {
            rejectJsonRpc(
                HttpStatusCode.BadRequest,
                RPCError.ErrorCode.INVALID_REQUEST,
                "Bad Request: the connection has not been initialised",
            )
            return false
        }

        return true
    }

    private suspend fun ApplicationCall.admitCredential(): Boolean {
        val result = auth.authenticate(
            authorization = request.header(HttpHeaders.Authorization),
            queryParameterNames = request.queryParameters.names(),
        )

        return when (result) {
            AuthResult.Authenticated -> true

            is AuthResult.Refused -> {
                response.header(HttpHeaders.WWWAuthenticate, auth.challenge(resourceMetadataUrl, result.reason))
                rejectJsonRpc(
                    HttpStatusCode.Unauthorized,
                    RPCError.ErrorCode.INVALID_REQUEST,
                    "Unauthorized: ${result.reason.description}",
                )
                false
            }
        }
    }

    /**
     * Closes the connection in force, if any, and builds a fresh one.
     *
     * Called on every initialisation, which is what lets a client that restarted connect again:
     * the SDK's session refuses a second `initialize`, so reconnecting is a new session or it is
     * nothing.
     */
    private suspend fun rebuildConnection(): Connection = mutex.withLock {
        connection?.let { runCatching { it.session.close() } }

        val transport = StreamableHttpServerTransport(
            StreamableHttpServerTransport.Configuration(enableJsonResponse = true),
        ).also {
            // No session identifier is generated, so none is ever assigned or demanded.
            it.setSessionIdGenerator(null)
        }

        val session = server.createSession(transport)
        declaredClient.observe(session)
        onSession(session)

        Connection(transport, session).also { connection = it }
    }

    private suspend fun ApplicationCall.rejectJsonRpc(status: HttpStatusCode, code: Int, message: String) {
        respondText(
            text = McpJson.encodeToString(JSONRPCError(id = null, error = RPCError(code = code, message = message))),
            contentType = ContentType.Application.Json,
            status = status,
        )
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 1_000L
    }
}
