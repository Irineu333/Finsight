package com.neoutils.finsight.mcp.transport

import com.neoutils.finsight.mcp.FakeMcpServerSettingsRepository
import com.neoutils.finsight.mcp.McpServerController
import com.neoutils.finsight.mcp.McpTestClient
import com.neoutils.finsight.mcp.REVISION
import com.neoutils.finsight.mcp.TestTool
import com.neoutils.finsight.mcp.asJson
import com.neoutils.finsight.mcp.contract.ToolRegistry
import com.neoutils.finsight.mcp.enabledSettings
import com.neoutils.finsight.mcp.freePort
import com.neoutils.finsight.mcp.noPrompts
import com.neoutils.finsight.mcp.noResources
import com.neoutils.finsight.mcp.request
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The transport, exercised over a real loopback socket by a client that is not the MCP SDK.
 *
 * Everything here is asserted on the wire — status codes and headers — because that is what a
 * third-party client sees, and every requirement in this group is stated about what that client
 * gets back.
 */
class McpHttpTransportTest {

    private val token = "3f2a9c1e7b40d85f3f2a9c1e7b40d85f"

    private val tool = TestTool(name = "finsight_probe")

    private lateinit var controller: McpServerController

    private lateinit var client: McpTestClient

    private var port = 0

    @BeforeTest
    fun listen() = runBlocking {
        port = freePort()
        controller = McpServerController(
            settings = FakeMcpServerSettingsRepository(enabledSettings(port, token)),
            tools = ToolRegistry(listOf(tool)),
            resources = noResources(),
            prompts = noPrompts(),
        )
        controller.start()
        client = McpTestClient(port, token)
    }

    @AfterTest
    fun close() = runBlocking { controller.stop() }

    // ── The exit barrier of this group ────────────────────────────────────────────────────

    @Test
    fun `it starts, negotiates initialize and answers a tool listing`() {
        val handshake = client.initialize().body().asJson()

        val result = handshake["result"]!!.jsonObject
        assertEquals(REVISION, result["protocolVersion"]!!.jsonPrimitive.content)
        assertEquals("finsight", result["serverInfo"]!!.jsonObject["name"]!!.jsonPrimitive.content)

        val listing = client.post(request(id = 2, method = "tools/list")).body().asJson()
        val listed = listing["result"]!!.jsonObject["tools"]!!.jsonArray

        assertEquals(listOf("finsight_probe"), listed.map { it.jsonObject["name"]!!.jsonPrimitive.content })
    }

    @Test
    fun `a server with no tools answers an empty listing, not a failure`() = runBlocking {
        val emptyPort = freePort()
        val bare = McpServerController(
            settings = FakeMcpServerSettingsRepository(enabledSettings(emptyPort, token)),
            tools = ToolRegistry(emptyList()),
            resources = noResources(),
            prompts = noPrompts(),
        )
        bare.start()

        try {
            val bareClient = McpTestClient(emptyPort, token)
            val handshake = bareClient.initialize().body().asJson()["result"]!!.jsonObject
            assertEquals(REVISION, handshake["protocolVersion"]!!.jsonPrimitive.content)

            val listing = bareClient.post(request(id = 1, method = "tools/list")).body().asJson()

            assertEquals(0, listing["result"]!!.jsonObject["tools"]!!.jsonArray.size)
        } finally {
            bare.stop()
        }
    }

    @Test
    fun `the capabilities it negotiates include the tool list change notice and no logging`() {
        val capabilities = client.initialize().body().asJson()["result"]!!
            .jsonObject["capabilities"]!!.jsonObject

        assertEquals(true, capabilities["tools"]!!.jsonObject["listChanged"]!!.jsonPrimitive.content.toBoolean())
        assertNull(capabilities["logging"], "logging is deprecated by the next revision and is not offered")
    }

    // ── Origin ───────────────────────────────────────────────────────────────────────────

    @Test
    fun `an unrecognised Origin is refused with 403 before any tool runs`() {
        client.initialize()

        val response = client.post(
            body = request(
                id = 3,
                method = "tools/call",
                params = """{"name":"finsight_probe","arguments":{}}""",
            ),
            origin = "https://evil.example.com",
        )

        assertEquals(403, response.statusCode())
        assertEquals(0, tool.calls, "the tool must not have run")
    }

    @Test
    fun `the Origin check precedes the credential check`() {
        // No token at all, and a hostile Origin: the answer is 403, not 401. Refusing on the
        // Origin first is what keeps a rebound page from learning whether it guessed the token.
        val response = client.post(
            body = request(id = 4, method = "tools/list"),
            origin = "https://evil.example.com",
            bearer = null,
        )

        assertEquals(403, response.statusCode())
    }

    @Test
    fun `a loopback Origin is recognised, whatever its port`() {
        client.initialize()

        val response = client.post(request(id = 5, method = "tools/list"), origin = "http://localhost:5173")

        assertEquals(200, response.statusCode())
    }

    @Test
    fun `no Origin at all is admitted, because a non-browser client cannot be rebound`() {
        assertEquals(200, client.initialize().statusCode())
    }

    // ── The address ──────────────────────────────────────────────────────────────────────

    @Test
    fun `the socket is bound to loopback and to nothing else`() {
        val routable = NetworkInterface.getNetworkInterfaces().toList()
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { !it.isLoopbackAddress && !it.isAnyLocalAddress }

        if (routable == null) return // No non-loopback address on this machine: nothing to prove against.

        val reached = runCatching {
            Socket().use { it.connect(InetSocketAddress(routable, port), CONNECT_TIMEOUT_MILLIS) }
        }.isSuccess

        assertFalse(reached, "the server answered on ${routable.hostAddress}, which is not loopback")
    }

    // ── Sessions ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `no session identifier is ever assigned`() {
        val handshake = client.initialize()

        assertTrue(handshake.headers().firstValue("mcp-session-id").isEmpty)

        // And a following request needs to carry none.
        val listing = client.post(request(id = 6, method = "tools/list"))
        assertEquals(200, listing.statusCode())
        assertTrue(listing.headers().firstValue("mcp-session-id").isEmpty)
    }

    // ── The protocol version header ──────────────────────────────────────────────────────

    @Test
    fun `an unsupported protocol version is refused with 400`() {
        client.initialize()

        val response = client.post(request(id = 7, method = "tools/list"), protocolVersion = "1999-01-01")

        assertEquals(400, response.statusCode())
    }

    @Test
    fun `a malformed protocol version is refused with 400`() {
        client.initialize()

        assertEquals(400, client.post(request(id = 8, method = "tools/list"), protocolVersion = "").statusCode())
        assertEquals(400, client.post(request(id = 9, method = "tools/list"), protocolVersion = "latest").statusCode())
    }

    @Test
    fun `a GET without the version header is refused with 400`() {
        client.initialize()

        val response = java.net.http.HttpClient.newHttpClient().send(
            java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://$LOOPBACK_HOST:$port$MCP_ENDPOINT_PATH"))
                .header("Accept", "text/event-stream")
                .header("Authorization", "Bearer $token")
                .GET()
                .build(),
            java.net.http.HttpResponse.BodyHandlers.ofString(),
        )

        // The exception to "required" is the initialisation POST alone; the notification stream
        // never gets it.
        assertEquals(400, response.statusCode())
    }

    // ── The credential ───────────────────────────────────────────────────────────────────

    @Test
    fun `a request without a token is refused with 401 and a legible challenge`() {
        val response = McpTestClient(port, token = null).initialize()

        assertEquals(401, response.statusCode())
        val challenge = response.headers().firstValue("WWW-Authenticate").orElse("")
        assertTrue(challenge.startsWith("Bearer "), challenge)
        assertTrue(challenge.contains(PROTECTED_RESOURCE_METADATA_PATH), challenge)
    }

    @Test
    fun `a wrong token is refused with 401`() {
        val response = McpTestClient(port, token = "not-the-token").initialize()

        assertEquals(401, response.statusCode())
        assertTrue(response.headers().firstValue("WWW-Authenticate").orElse("").contains("invalid_token"))
    }

    @Test
    fun `the document the challenge points at is reachable without a credential`() {
        val metadata = McpTestClient(port, token = null).let {
            java.net.http.HttpClient.newHttpClient().send(
                java.net.http.HttpRequest.newBuilder(
                    java.net.URI.create("http://$LOOPBACK_HOST:$port$PROTECTED_RESOURCE_METADATA_PATH"),
                ).build(),
                java.net.http.HttpResponse.BodyHandlers.ofString(),
            )
        }

        assertEquals(200, metadata.statusCode())
        val document = metadata.body().asJson()
        assertEquals("http://$LOOPBACK_HOST:$port$MCP_ENDPOINT_PATH", document["resource"]!!.jsonPrimitive.content)
        assertEquals(
            listOf("header"),
            (document["bearer_methods_supported"] as JsonArray).map { it.jsonPrimitive.content },
        )
    }

    @Test
    fun `a token in the query string is refused, even when the header carries the right one`() {
        val response = client.post(
            body = request(id = 11, method = "tools/list"),
            query = "?access_token=$token",
        )

        assertEquals(401, response.statusCode())
    }

    @Test
    fun `no response ever repeats the token`() {
        val responses = listOf(
            client.initialize(),
            client.post(request(id = 12, method = "tools/list")),
            McpTestClient(port, token = null).initialize(),
            client.post(request(id = 13, method = "tools/list"), origin = "https://evil.example.com"),
        )

        responses.forEach { response ->
            assertFalse(response.body().contains(token), "a response body carried the token")
            response.headers().map().values.flatten().forEach {
                assertFalse(it.contains(token), "a response header carried the token")
            }
        }
    }

    // ── The endpoint ─────────────────────────────────────────────────────────────────────

    @Test
    fun `there is a single endpoint path`() {
        val elsewhere = java.net.http.HttpClient.newHttpClient().send(
            java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://$LOOPBACK_HOST:$port/rpc"))
                .header("Authorization", "Bearer $token")
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString("{}"))
                .build(),
            java.net.http.HttpResponse.BodyHandlers.ofString(),
        )

        assertEquals(404, elsewhere.statusCode())
    }

    @Test
    fun `a client that reconnects can initialise again`() {
        assertEquals(200, client.initialize("first-client").statusCode())

        val second = client.initialize("second-client").body().asJson()

        assertEquals(REVISION, second["result"]!!.jsonObject["protocolVersion"]!!.jsonPrimitive.content)
        assertFalse(second.containsKey("error"), second.toString())
    }

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 750
    }
}
