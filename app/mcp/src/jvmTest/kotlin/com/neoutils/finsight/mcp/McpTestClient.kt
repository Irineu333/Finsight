package com.neoutils.finsight.mcp

import com.neoutils.finsight.feature.mcp.api.IMcpServerSettingsRepository
import com.neoutils.finsight.feature.mcp.api.McpPermission
import com.neoutils.finsight.feature.mcp.api.McpServerSettings
import com.neoutils.finsight.mcp.contract.McpTool
import com.neoutils.finsight.mcp.contract.ToolAnnotations
import com.neoutils.finsight.mcp.contract.ToolOutcome
import com.neoutils.finsight.mcp.contract.toolOutcomeSchema
import com.neoutils.finsight.mcp.transport.LOOPBACK_HOST
import com.neoutils.finsight.mcp.transport.MCP_ENDPOINT_PATH
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** The revision this suite speaks, spelled out rather than read from the SDK it verifies. */
const val REVISION: String = "2025-11-25"

/**
 * A real HTTP client over a real loopback socket.
 *
 * Deliberately the JDK's client and not the MCP SDK's: a test that spoke to the server through
 * the same library the server is built on would agree with it about everything, including about
 * a header neither of them sends.
 */
class McpTestClient(private val port: Int, private val token: String?) {

    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
        .build()

    val endpoint: String get() = "http://$LOOPBACK_HOST:$port$MCP_ENDPOINT_PATH"

    /**
     * Sends [body] to the endpoint.
     *
     * @param protocolVersion the value of the version header, or `null` to omit it — which is
     * what an `initialize` does.
     * @param origin the `Origin` header, or `null` to omit it, as a non-browser client does.
     * @param query appended to the path verbatim, for the query-string refusal.
     */
    fun post(
        body: String,
        protocolVersion: String? = REVISION,
        origin: String? = null,
        query: String = "",
        bearer: String? = token,
        timeoutMillis: Long = TIMEOUT_SECONDS * 1_000,
    ): HttpResponse<String> {
        val request = HttpRequest.newBuilder(URI.create(endpoint + query))
            .timeout(Duration.ofMillis(timeoutMillis))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json, text/event-stream")
            .apply {
                bearer?.let { header("Authorization", "Bearer $it") }
                protocolVersion?.let { header("MCP-Protocol-Version", it) }
                origin?.let { header("Origin", it) }
            }
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()

        return client.send(request, HttpResponse.BodyHandlers.ofString())
    }

    /** Opens the notification stream, returning the lines it carries as they arrive. */
    fun openNotificationStream(): HttpResponse<java.util.stream.Stream<String>> {
        val request = HttpRequest.newBuilder(URI.create(endpoint))
            .header("Accept", "text/event-stream")
            .header("MCP-Protocol-Version", REVISION)
            .apply { token?.let { header("Authorization", "Bearer $it") } }
            .GET()
            .build()

        return client.send(request, HttpResponse.BodyHandlers.ofLines())
    }

    /** Performs the handshake: `initialize`, then the notification that follows it. */
    fun initialize(clientName: String = "finsight-test-client"): HttpResponse<String> {
        val result = post(
            body = request(
                id = INITIALIZE_ID,
                method = "initialize",
                params = """
                    {"protocolVersion":"$REVISION","capabilities":{},
                     "clientInfo":{"name":"$clientName","version":"1"}}
                """.trimIndent(),
            ),
            protocolVersion = null,
        )
        post(body = """{"jsonrpc":"2.0","method":"notifications/initialized"}""")
        return result
    }

    private companion object {
        const val TIMEOUT_SECONDS = 10L
        const val INITIALIZE_ID = 1
    }
}

/** One JSON-RPC request, as text — the wire is what is being asserted about. */
fun request(id: Int, method: String, params: String? = null): String = buildString {
    append("""{"jsonrpc":"2.0","id":$id,"method":"$method"""")
    params?.let { append(""","params":$it""") }
    append("}")
}

/** One JSON-RPC notification, as text. */
fun notification(method: String, params: String): String =
    """{"jsonrpc":"2.0","method":"$method","params":$params}"""

fun String.asJson(): JsonObject = Json.parseToJsonElement(this) as JsonObject

/** A port nothing is listening on right now. */
fun freePort(): Int = ServerSocket(0).use { it.localPort }

/** Whether anything is listening on [port] of the loopback address. */
fun isListening(port: Int): Boolean = runCatching {
    Socket().use { it.connect(InetSocketAddress(InetAddress.getByName(LOOPBACK_HOST), port), 500) }
}.isSuccess

/** A tool whose whole behaviour the test dictates. */
class TestTool(
    override val name: String,
    private val write: Boolean = false,
    private val body: suspend (JsonObject) -> ToolOutcome = { ToolOutcome.Ok(JsonObject(emptyMap())) },
) : McpTool {

    /** How many times [execute] was entered. Zero is the proof that nothing ran. */
    var calls: Int = 0
        private set

    override val title: String get() = name
    override val description: String get() = "A tool that exists so a test can observe the server."
    override val inputSchema: JsonObject get() = Json.parseToJsonElement("""{"type":"object"}""") as JsonObject
    override val outputSchema: JsonObject get() = toolOutcomeSchema(inputSchema, setOf("BROKEN"))
    override val annotations: ToolAnnotations get() = ToolAnnotations(readOnlyHint = !write)

    override suspend fun execute(arguments: JsonObject): ToolOutcome {
        calls++
        return body(arguments)
    }
}

/** Settings with a server that is on, at [permission], on [port]. */
fun enabledSettings(port: Int, token: String, permission: McpPermission = McpPermission.READ_WRITE) =
    McpServerSettings(isEnabled = true, permission = permission, port = port, token = token)

/** The settings the controller reads, driven by the test rather than by a database. */
class FakeMcpServerSettingsRepository(initial: McpServerSettings) : IMcpServerSettingsRepository {

    private val state = MutableStateFlow(initial)

    /** How many times the token was rotated — the observable half of "treated as compromised". */
    var rotations: Int = 0
        private set

    override fun observe(): StateFlow<McpServerSettings> = state.asStateFlow()

    override suspend fun setEnabled(isEnabled: Boolean) {
        state.value = state.value.copy(isEnabled = isEnabled)
    }

    override suspend fun setPermission(permission: McpPermission) {
        state.value = state.value.copy(permission = permission)
    }

    override suspend fun setPort(port: Int) {
        state.value = state.value.copy(port = port)
    }

    override suspend fun rotateToken(): String {
        rotations++
        val rotated = "rotated-token-$rotations"
        state.value = state.value.copy(token = rotated)
        return rotated
    }
}
