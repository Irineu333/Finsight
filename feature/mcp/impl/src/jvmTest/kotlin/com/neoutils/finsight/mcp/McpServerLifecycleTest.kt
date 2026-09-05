package com.neoutils.finsight.mcp

import com.neoutils.finsight.feature.mcp.api.McpServerController
import com.neoutils.finsight.feature.mcp.api.McpServerFailure
import com.neoutils.finsight.feature.mcp.api.McpServerState
import com.neoutils.finsight.mcp.McpServerHarness.Companion.freePort
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What the user chose about the server, across the closings and openings of the app that the choice
 * has to outlive — and what the app says when the choice cannot be honoured.
 */
class McpServerLifecycleTest {

    /**
     * The user switches it on once. Every launch after that has to honour it without a visit to the
     * settings section, or the agent connects according to whether the user remembered, and the
     * failure surfaces on the agent's side, far from its cause.
     */
    @Test
    fun `a server switched on comes back up by itself on the next launch`() = runTest {
        val settings = MapSettings()
        val port = freePort()

        McpServerHarness(settings).use { firstRun ->
            firstRun.controller.setPort(port)
            firstRun.controller.setEnabled(true)

            assertEquals(
                McpServerState.Running(port = port),
                firstRun.controller.state.value,
                "The server did not come up when the user switched it on.",
            )

            // The window closes. The choice is not touched by it.
            firstRun.controller.stop()
        }

        McpServerHarness(settings).use { secondRun ->
            // The whole of the second launch: the process starts, and nobody visits any screen.
            secondRun.controller.start()

            assertEquals(
                McpServerState.Running(port = port),
                secondRun.controller.state.value,
                "The server did not come back up on the next launch, so the user's choice did " +
                    "not survive the app closing.",
            )
            assertTrue(
                withContext(Dispatchers.IO) { !Loopback.refusesConnection(port) },
                "The state says the server is up and nothing accepts a connection at $port.",
            )

            secondRun.controller.stop()
        }
    }

    /** Switching it off is as durable as switching it on: off stays off across launches. */
    @Test
    fun `a server switched off stays down on the next launch`() = runTest {
        val settings = MapSettings()
        val port = freePort()

        McpServerHarness(settings).use { firstRun ->
            firstRun.controller.setPort(port)
            firstRun.controller.setEnabled(true)
            firstRun.controller.setEnabled(false)

            assertEquals(McpServerState.Stopped, firstRun.controller.state.value)
            firstRun.controller.stop()
        }

        McpServerHarness(settings).use { secondRun ->
            secondRun.controller.start()

            assertEquals(
                McpServerState.Stopped,
                secondRun.controller.state.value,
                "A server the user switched off came back up on its own.",
            )
            assertTrue(
                withContext(Dispatchers.IO) { Loopback.refusesConnection(port) },
                "Something is listening at $port for a server the user switched off.",
            )
        }
    }

    /**
     * The app updated and opened for the first time: nothing was ever chosen, so nothing comes up
     * and nothing listens.
     *
     * The port is the only thing seeded, and only so the test has an address to knock on. What is
     * absent is the choice — and a token, which would be evidence the app had prepared a server for
     * someone to connect to.
     */
    @Test
    fun `an installation where nobody has chosen yet listens to nothing`() = runTest {
        val port = freePort()
        val settings = MapSettings("mcp_server_port" to port)

        McpServerHarness(settings).use { harness ->
            harness.controller.start()

            assertEquals(
                McpServerState.Stopped,
                harness.controller.state.value,
                "A server nobody switched on came up.",
            )
            assertTrue(
                withContext(Dispatchers.IO) { Loopback.refusesConnection(port) },
                "Something accepted a connection at $port on an installation with no choice " +
                    "to honour.",
            )
            assertEquals(
                null,
                harness.controller.token.value,
                "A token was minted for a server that was never asked to run.",
            )
            assertTrue(
                "mcp_server_token" !in settings.keys,
                "A secret was written to the machine by an app that opened nothing.",
            )
        }
    }

    /**
     * A port already held is the one failure the user can act on, and the answer is to refuse to
     * come up rather than to move.
     *
     * Moving would be worse than failing: the client configured for the port finds nothing, and the
     * symptom — "the agent will not connect" — points nowhere near the cause (design D10).
     */
    @Test
    fun `a port already held stops the server and says which port it was`() = runTest {
        val port = freePort()

        ServerSocket(port, 0, InetAddress.getByName("127.0.0.1")).use { squatter ->
            McpServerHarness().use { harness ->
                harness.controller.setPort(port)
                harness.controller.setEnabled(true)

                assertEquals(
                    McpServerState.Failed(port = port, cause = McpServerFailure.PORT_IN_USE),
                    harness.controller.state.value,
                    "A server that could not bind is not reporting the port that stopped it.",
                )
                assertTrue(
                    squatter.isBound && !squatter.isClosed,
                    "The program that was holding the port lost it.",
                )
                assertEquals(
                    true,
                    harness.controller.isEnabled.value,
                    "The user's choice was discarded because the bind failed; the switch is on " +
                        "and the socket is the thing that did not happen.",
                )
            }
        }
    }

    /** The way out of the clash: the user moves the port, and the server comes up on the new one. */
    @Test
    fun `moving the port off a clash brings the server up`() = runTest {
        val taken = freePort()
        val free = freePort()

        ServerSocket(taken, 0, InetAddress.getByName("127.0.0.1")).use {
            McpServerHarness().use { harness ->
                harness.controller.setPort(taken)
                harness.controller.setEnabled(true)
                assertTrue(harness.controller.state.value is McpServerState.Failed)

                harness.controller.setPort(free)

                assertEquals(
                    McpServerState.Running(port = free),
                    harness.controller.state.value,
                    "Moving off the clash did not bring the server up.",
                )

                harness.controller.stop()
            }
        }
    }

    /** Closing the app releases the port, or a relaunch races the socket it just left behind. */
    @Test
    fun `stopping releases the port`() = runTest {
        val port = freePort()

        McpServerHarness().use { harness ->
            harness.controller.setPort(port)
            harness.controller.setEnabled(true)
            harness.controller.stop()

            assertEquals(McpServerState.Stopped, harness.controller.state.value)
            assertTrue(
                withContext(Dispatchers.IO) { Loopback.refusesConnection(port) },
                "The port is still accepting connections after the server was stopped.",
            )
            // The strongest evidence the port is free: taking it.
            ServerSocket(port, 0, InetAddress.getByName("127.0.0.1")).close()
        }
    }

    /** The default is a decision, and a decision that changes is one a client has to be told about. */
    @Test
    fun `the default port is the one the design chose`() {
        assertEquals(8477, McpServerController.DEFAULT_PORT)
    }
}
