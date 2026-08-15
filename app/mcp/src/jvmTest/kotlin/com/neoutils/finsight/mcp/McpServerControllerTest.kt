package com.neoutils.finsight.mcp

import com.neoutils.finsight.feature.mcp.api.McpPermission
import com.neoutils.finsight.feature.mcp.api.McpServerSettings
import com.neoutils.finsight.mcp.contract.ToolRegistry
import com.neoutils.finsight.mcp.transport.LOOPBACK_HOST
import com.neoutils.finsight.mcp.transport.MCP_ENDPOINT_PATH
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class McpServerControllerTest {

    private val token = "controller-token"

    private val readTool = TestTool(name = "finsight_read")

    private val writeTool = TestTool(name = "finsight_write", write = true)

    private val registry = ToolRegistry(listOf(readTool, writeTool))

    @Test
    fun `off means nothing listening`() = runBlocking {
        val port = freePort()
        val controller = controller(
            McpServerSettings(isEnabled = false, permission = McpPermission.READ_ONLY, port = port, token = token),
        )

        controller.start()

        try {
            assertEquals(McpServerState.Stopped, controller.state.value)
            assertFalse(isListening(port), "a socket exists while the server is off")
        } finally {
            controller.stop()
        }
    }

    @Test
    fun `on means listening at the persisted address, with the level in force`() = runBlocking {
        val port = freePort()
        val controller = controller(enabledSettings(port, token, McpPermission.READ_ONLY))

        controller.start()

        try {
            assertEquals(
                McpServerState.Listening("http://$LOOPBACK_HOST:$port$MCP_ENDPOINT_PATH", McpPermission.READ_ONLY),
                controller.state.value,
            )
            assertTrue(isListening(port))
        } finally {
            controller.stop()
        }
    }

    @Test
    fun `stopping closes the socket`() = runBlocking {
        val port = freePort()
        val controller = controller(enabledSettings(port, token))
        controller.start()

        controller.stop()

        assertEquals(McpServerState.Stopped, controller.state.value)
        assertFalse(isListening(port), "the socket outlived the server")
    }

    @Test
    fun `switching off at runtime closes the socket`() = runBlocking {
        val port = freePort()
        val settings = FakeMcpServerSettingsRepository(enabledSettings(port, token))
        val controller = McpServerController(settings, registry)
        controller.start()
        assertTrue(isListening(port))

        try {
            settings.setEnabled(false)
            awaitState(controller) { it == McpServerState.Stopped }

            assertFalse(isListening(port))
        } finally {
            controller.stop()
        }
    }

    @Test
    fun `an occupied port fails to start, and no other port is taken in silence`() = runBlocking {
        val port = freePort()
        ServerSocket().use { squatter ->
            squatter.reuseAddress = false
            squatter.bind(InetSocketAddress(InetAddress.getByName(LOOPBACK_HOST), port))

            val controller = controller(enabledSettings(port, token))
            controller.start()

            try {
                val state = assertIs<McpServerState.PortUnavailable>(controller.state.value)
                assertEquals(port, state.port)
                assertTrue(state.reason.isNotBlank(), "the conflict has to be nameable on the screen")

                // Nothing is serving MCP: the only thing on that port is the other process.
                val client = McpTestClient(port, token)
                val reached = runCatching { withContext(Dispatchers.IO) { client.initialize() } }
                assertTrue(reached.isFailure, "the server answered on a port it was told was taken")
            } finally {
                controller.stop()
            }
        }
    }

    @Test
    fun `read-only announces no writes, and refuses one by permission rather than by absence`() = runBlocking {
        val port = freePort()
        val controller = controller(enabledSettings(port, token, McpPermission.READ_ONLY))
        controller.start()

        try {
            val client = McpTestClient(port, token)
            withContext(Dispatchers.IO) { client.initialize() }

            val announced = withContext(Dispatchers.IO) { client.post(request(2, "tools/list")) }
                .body().asJson()["result"]!!.jsonObject["tools"]!!.jsonArray
                .map { it.jsonObject["name"]!!.jsonPrimitive.content }
            assertEquals(listOf("finsight_read"), announced)

            val refused = withContext(Dispatchers.IO) {
                client.post(request(3, "tools/call", """{"name":"finsight_write","arguments":{}}"""))
            }.body().asJson()["result"]!!.jsonObject

            // A tool execution error, not a protocol error: the server understood the call and
            // refused it. And the refusal names the permission — never "no such tool", which
            // would send the agent looking for a spelling mistake.
            assertEquals(true, refused["isError"]!!.jsonPrimitive.content.toBoolean())
            val error = refused["structuredContent"]!!.jsonObject["error"]!!.jsonObject
            assertEquals(PERMISSION_REFUSED_CODE, error["code"]!!.jsonPrimitive.content)
            // Told apart from a rule of the domain by its class, and not retryable: nothing
            // changes until a human grants the level, so "try again" would be a loop.
            assertEquals("PERMISSION", error["category"]!!.jsonPrimitive.content)
            assertEquals(false, error["isRetryable"]!!.jsonPrimitive.content.toBoolean())
            assertEquals(0, writeTool.calls, "the domain must not have been reached")
        } finally {
            controller.stop()
        }
    }

    @Test
    fun `changing the level at runtime emits the tool list change notice`() = runBlocking {
        val port = freePort()
        val settings = FakeMcpServerSettingsRepository(enabledSettings(port, token, McpPermission.READ_ONLY))
        val controller = McpServerController(settings, registry)
        controller.start()

        try {
            val client = McpTestClient(port, token)
            withContext(Dispatchers.IO) { client.initialize() }

            val notices = LinkedBlockingQueue<String>()
            val stream = thread(isDaemon = true) {
                runCatching {
                    client.openNotificationStream().body().forEach { line -> notices.offer(line) }
                }
            }
            // The standalone stream has to be registered before the level moves, or the notice
            // has nowhere to land.
            delay(STREAM_SETTLE_MILLIS)

            settings.setPermission(McpPermission.READ_WRITE)
            awaitState(controller) { it is McpServerState.Listening && it.permission == McpPermission.READ_WRITE }

            val notice = generateSequence { notices.poll(NOTICE_TIMEOUT_SECONDS, TimeUnit.SECONDS) }
                .take(MAX_LINES)
                .firstOrNull { it.contains("notifications/tools/list_changed") }
            stream.interrupt()

            assertNotNull(notice, "no tool list change notice reached the open stream")

            // And the listing the client now gets is the one the notice was about.
            val announced = withContext(Dispatchers.IO) { client.post(request(4, "tools/list")) }
                .body().asJson()["result"]!!.jsonObject["tools"]!!.jsonArray
                .map { it.jsonObject["name"]!!.jsonPrimitive.content }
            assertContains(announced, "finsight_write")
        } finally {
            controller.stop()
        }
    }

    @Test
    fun `changing the port at runtime moves the server, and leaves nothing behind`() = runBlocking {
        val first = freePort()
        val second = freePort()
        val settings = FakeMcpServerSettingsRepository(enabledSettings(first, token))
        val controller = McpServerController(settings, registry)
        controller.start()
        assertTrue(isListening(first))

        try {
            settings.setPort(second)
            awaitState(controller) { it is McpServerState.Listening && it.url.contains(":$second") }

            assertTrue(isListening(second))
            assertFalse(isListening(first), "the old address is still answering")
        } finally {
            controller.stop()
        }
    }

    @Test
    fun `starting twice does not start twice`() = runBlocking {
        val port = freePort()
        val controller = controller(enabledSettings(port, token))

        controller.start()
        controller.start()

        try {
            assertIs<McpServerState.Listening>(controller.state.value)
            assertTrue(isListening(port))
        } finally {
            controller.stop()
        }
    }

    private fun controller(settings: McpServerSettings) =
        McpServerController(FakeMcpServerSettingsRepository(settings), registry)

    private suspend fun awaitState(controller: McpServerController, predicate: (McpServerState) -> Boolean) {
        repeat(POLLS) {
            if (predicate(controller.state.value)) return
            delay(POLL_MILLIS)
        }
        throw AssertionError("The controller stayed at ${controller.state.value}")
    }

    private companion object {
        const val POLLS = 100
        const val POLL_MILLIS = 50L
        const val STREAM_SETTLE_MILLIS = 500L
        const val NOTICE_TIMEOUT_SECONDS = 5L
        const val MAX_LINES = 50
    }
}
