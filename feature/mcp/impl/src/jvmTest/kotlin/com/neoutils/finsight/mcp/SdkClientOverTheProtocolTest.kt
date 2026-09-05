package com.neoutils.finsight.mcp

import com.neoutils.finsight.feature.mcp.api.McpPermissionAxis
import com.neoutils.finsight.mcp.McpServerHarness.Companion.freePort
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.sse.SSE
import io.ktor.http.HttpHeaders
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.Method
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolListChangedNotification
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The other end of the same SDK, against the server this app already runs.
 *
 * Every other test here writes the protocol by hand, because what they are about — the perimeter,
 * a refusal, the shape of a payload — is only visible byte for byte. This one is about the
 * opposite: that a *client library* holding the whole handshake, the session header and the
 * standalone event stream reaches this server and is answered. That is the premise the bridge
 * rests on, where a stdio session forwards to the window's embedded server through a `Client` of
 * this same SDK (design D8), and a premise that fails here would be discovered with the bridge
 * already written on top of it.
 *
 * Nothing is stubbed: the server is [DesktopMcpServerController] over a real socket, behind its
 * token, and the client is the SDK's own.
 */
class SdkClientOverTheProtocolTest {

    /**
     * The three exchanges a bridge has to complete before it can forward anything: the handshake
     * that opens the session, the list it will pass on, and a call whose answer it will pass back.
     */
    @Test
    fun `the sdk client initialises, lists and calls against the embedded server`() = runBlocking {
        val port = freePort()
        val tool = SpyTool(name = McpToolName.LIST_ACCOUNTS.wireName, effect = McpToolEffect.READS)

        McpServerHarness(tools = listOf(tool)).use { harness ->
            harness.controller.setPort(port)
            harness.controller.setEnabled(true)
            val token = assertNotNull(harness.controller.token.value, "the server minted no token")

            connected(port, token) { client ->
                assertNotNull(
                    client.serverCapabilities?.tools,
                    "The handshake did not declare tools, so a client library has no reason to " +
                        "list them.",
                )

                val listed = client.listTools().tools.map { it.name }
                assertEquals(
                    listOf(tool.name),
                    listed,
                    "The SDK client did not read back the tool the server offers.",
                )

                val answer = client.callTool(name = tool.name, arguments = emptyMap())
                assertEquals(
                    "done",
                    (answer.content.single() as TextContent).text,
                    "The call came back with something other than what the tool answered.",
                )
                assertEquals(1, tool.calls, "The tool did not run once for one call.")
            }

            harness.controller.stop()
        }
    }

    /**
     * The user moves a switch while the agent is connected.
     *
     * A bridge that only forwarded requests would leave the client believing the list it read at
     * the start, so the announcement has to arrive at a real client and not only on a raw stream
     * the test reads itself.
     *
     * The switch is moved **until** one is heard, and not once. The transport opens the standalone
     * event stream on a coroutine of its own once the handshake is over, nothing in the client or
     * the server says when it came up, and an announcement sent before it is up is gone — the
     * protocol has no replay for one. Hearing an announcement is the only signal there is, so the
     * loop is the synchronisation and not a retry over a flaky server.
     */
    @Test
    fun `a permission moved reaches the sdk client as a tool list changed notification`() = runBlocking {
        val port = freePort()
        val tool = SpyTool(name = McpToolName.LIST_ACCOUNTS.wireName, effect = McpToolEffect.READS)

        McpServerHarness(tools = listOf(tool)).use { harness ->
            harness.controller.setPort(port)
            harness.controller.setEnabled(true)
            val token = assertNotNull(harness.controller.token.value, "the server minted no token")

            connected(port, token) { client ->
                val announced = CompletableDeferred<Unit>()
                client.setNotificationHandler<ToolListChangedNotification>(
                    Method.Defined.NotificationsToolsListChanged,
                ) {
                    announced.complete(Unit)
                    CompletableDeferred(Unit)
                }

                withTimeout(ANNOUNCEMENT_TIMEOUT_MILLIS) {
                    while (!announced.isCompleted) {
                        harness.controller.setPermission(McpPermissionAxis.READ, granted = false)
                        delay(SETTLE_MILLIS)
                        if (announced.isCompleted) break
                        harness.controller.setPermission(McpPermissionAxis.READ, granted = true)
                        delay(SETTLE_MILLIS)
                    }
                    announced.await()
                }

                // Whatever half of the toggling the loop left behind, the axis ends withheld — and
                // moving it to where it already is announces nothing.
                harness.controller.setPermission(McpPermissionAxis.READ, granted = false)

                assertTrue(
                    client.listTools().tools.isEmpty(),
                    "The client was told the list changed and read the old one back.",
                )
            }

            harness.controller.stop()
        }
    }

    /**
     * Opens a session with the SDK's client and closes it afterwards, whatever [block] does.
     *
     * On [Dispatchers.IO] because the whole exchange is socket work, and the standalone event
     * stream the transport opens after the handshake lives for as long as the session does.
     */
    private suspend fun connected(
        port: Int,
        token: String,
        block: suspend (Client) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val http = HttpClient(OkHttp) { install(SSE) }
        val client = Client(Implementation(name = "finsight-bridge-test", version = "1"))

        try {
            client.connect(
                StreamableHttpClientTransport(client = http, url = "http://127.0.0.1:$port/mcp") {
                    headers.append(HttpHeaders.Authorization, "Bearer $token")
                },
            )
            block(client)
        } finally {
            client.close()
            http.close()
        }
    }

    private companion object {

        /**
         * The deadline on the loop as a whole: an announcement that never arrives fails the run
         * instead of hanging it. Measured on this machine, the stream comes up and the first
         * announcement is heard around 200 ms in.
         */
        const val ANNOUNCEMENT_TIMEOUT_MILLIS = 20_000L

        /** How long one turn of the loop leaves the announcement to travel over loopback. */
        const val SETTLE_MILLIS = 100L
    }
}
