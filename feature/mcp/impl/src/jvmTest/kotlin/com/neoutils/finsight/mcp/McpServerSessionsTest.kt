package com.neoutils.finsight.mcp

import com.neoutils.finsight.feature.mcp.api.McpServerState
import com.neoutils.finsight.mcp.McpServerHarness.Companion.freePort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Who is connected, and the user's power to end it.
 *
 * Being switched on and having someone on the other side are different facts, and only the second
 * one means something may be reading the finances at this moment.
 */
class McpServerSessionsTest {

    @Test
    fun `a server nobody is talking to reports no sessions`() = runTest {
        val port = freePort()

        McpServerHarness().use { harness ->
            harness.controller.setPort(port)
            harness.controller.setEnabled(true)

            assertEquals(
                McpServerState.Running(port = port, sessions = 0),
                harness.controller.state.value,
            )

            harness.controller.stop()
        }
    }

    @Test
    fun `each client that opens a session is counted`() = runTest {
        val port = freePort()

        McpServerHarness().use { harness ->
            harness.controller.setPort(port)
            harness.controller.setEnabled(true)
            val token = assertNotNull(harness.controller.token.value)

            withContext(Dispatchers.IO) { McpConversation(port, token).open() }

            assertEquals(
                McpServerState.Running(port = port, sessions = 1),
                harness.controller.state.value,
                "A client with a session open is not being counted.",
            )

            withContext(Dispatchers.IO) { McpConversation(port, token).open() }

            assertEquals(
                McpServerState.Running(port = port, sessions = 2),
                harness.controller.state.value,
                "A second client with a session open is not being counted.",
            )

            harness.controller.stop()
        }
    }

    /**
     * Ending the sessions is not switching the server off: whoever was connected is disconnected,
     * and the server keeps listening for whoever the user does want.
     */
    @Test
    fun `ending the sessions disconnects the clients and leaves the server up`() = runTest {
        val port = freePort()

        McpServerHarness().use { harness ->
            harness.controller.setPort(port)
            harness.controller.setEnabled(true)
            val token = assertNotNull(harness.controller.token.value)

            val client = withContext(Dispatchers.IO) { McpConversation(port, token).open() }
            assertEquals(McpServerState.Running(port = port, sessions = 1), harness.controller.state.value)

            harness.controller.disconnectSessions()

            assertEquals(
                McpServerState.Running(port = port, sessions = 0),
                harness.controller.state.value,
                "The sessions were ended and the count still shows someone connected.",
            )

            val afterwards = withContext(Dispatchers.IO) { client.listTools() }
            assertTrue(
                afterwards.status == 404 || afterwards.status == 400,
                "A client whose session was ended is still being served: " +
                    "${afterwards.status} ${afterwards.body}",
            )

            // Still listening: a new client connects without the server being switched on again.
            val newcomer = withContext(Dispatchers.IO) { McpConversation(port, token).initialize() }
            assertEquals(
                200,
                newcomer.status,
                "The server stopped listening when the sessions were ended: ${newcomer.body}",
            )

            harness.controller.stop()
        }
    }

    /** Taking the server down takes its sessions with it — nothing survives the socket. */
    @Test
    fun `stopping the server leaves no session behind`() = runTest {
        val port = freePort()

        McpServerHarness().use { harness ->
            harness.controller.setPort(port)
            harness.controller.setEnabled(true)
            val token = assertNotNull(harness.controller.token.value)
            withContext(Dispatchers.IO) { McpConversation(port, token).open() }

            harness.controller.stop()

            assertEquals(McpServerState.Stopped, harness.controller.state.value)

            harness.controller.start()
            assertEquals(
                McpServerState.Running(port = port, sessions = 0),
                harness.controller.state.value,
                "A session outlived the socket that carried it.",
            )

            harness.controller.stop()
        }
    }
}
