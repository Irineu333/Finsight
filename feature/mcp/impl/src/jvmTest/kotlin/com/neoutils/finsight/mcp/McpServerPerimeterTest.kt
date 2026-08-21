package com.neoutils.finsight.mcp

import com.neoutils.finsight.mcp.McpServerHarness.Companion.freePort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import java.net.ServerSocket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The perimeter: the machine the app is running on, and nothing past it.
 *
 * Two different attackers are answered here. The one that has to come over the network is answered
 * by the address the socket is bound to — it is never established at all. The one that is *already*
 * inside, a page open in the user's own browser posting to `127.0.0.1` directly or through DNS
 * rebinding, reaches a socket that will talk to it, and is answered by reading `Host` and `Origin`
 * (design D11). Neither defence covers the other's case.
 */
class McpServerPerimeterTest {

    private fun tools() = listOf(SpyTool(name = "list_accounts", effect = McpToolEffect.READS))

    /**
     * A connection from another host is never established, because the server is not on the
     * interface it would arrive through.
     *
     * The machine's own external addresses stand in for that other host: they are the addresses a
     * second machine would use, and they are as far outside loopback as this test can reach without
     * one. Two things are asked of each — that nothing answers there, and that the address is free
     * for the taking, which is what rules out a wildcard bind that merely happened to refuse.
     */
    @Test
    fun `the socket is not on any interface but loopback`() = runTest {
        val port = freePort()

        McpServerHarness(tools = tools()).use { harness ->
            harness.controller.setPort(port)
            harness.controller.setEnabled(true)

            withContext(Dispatchers.IO) {
                // A machine whose only interface is loopback cannot stand in for a second machine,
                // and its silence proves nothing either way. The control below still runs.
                Loopback.externalAddresses().forEach { address ->
                    assertTrue(
                        Loopback.refusesConnection(port, address.hostAddress),
                        "The server answered at ${address.hostAddress}:$port, which is an " +
                            "address another machine can reach.",
                    )
                    ServerSocket(port, 0, address).use { taken ->
                        assertTrue(
                            taken.isBound,
                            "The server is holding ${address.hostAddress}:$port, so it is bound " +
                                "wider than loopback.",
                        )
                    }
                }

                // The control: the perimeter is a boundary and not a wall, and a server nobody can
                // reach would pass every assertion above for the wrong reason.
                assertTrue(
                    !Loopback.refusesConnection(port),
                    "Nothing is listening on loopback either, so the assertions above say nothing.",
                )
            }

            harness.controller.stop()
        }
    }

    /**
     * A page on a third-party site, running in the user's browser, is refused — and refused with a
     * valid token, so what the test measures is the origin check and not the token.
     */
    @Test
    fun `a request carrying a third-party origin is refused`() = runTest {
        val tool = SpyTool(name = "list_accounts", effect = McpToolEffect.READS)
        val port = freePort()

        McpServerHarness(tools = listOf(tool)).use { harness ->
            harness.controller.setPort(port)
            harness.controller.setEnabled(true)
            val token = assertNotNull(harness.controller.token.value)

            val response = withContext(Dispatchers.IO) {
                McpConversation(port = port, token = token, origin = "https://evil.example.com")
                    .initialize()
            }

            assertEquals(
                403,
                response.status,
                "A request from a third-party origin was let through: ${response.body}",
            )
            assertEquals(0, tool.calls, "A tool ran for a third-party origin.")

            harness.controller.stop()
        }
    }

    /**
     * DNS rebinding works by resolving a name the attacker owns to `127.0.0.1`, so the browser
     * sends the attacker's name in `Host` while talking to this machine. Reading `Host` is what
     * catches it.
     */
    @Test
    fun `a request carrying a rebound host is refused`() = runTest {
        val tool = SpyTool(name = "list_accounts", effect = McpToolEffect.READS)
        val port = freePort()

        McpServerHarness(tools = listOf(tool)).use { harness ->
            harness.controller.setPort(port)
            harness.controller.setEnabled(true)
            val token = assertNotNull(harness.controller.token.value)

            val response = withContext(Dispatchers.IO) {
                RawHttp.post(
                    port = port,
                    body = """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"t","version":"1"}}}""",
                    token = token,
                    host = "attacker.example.com:$port",
                )
            }

            assertEquals(
                403,
                response.status,
                "A request whose Host is a name the attacker owns was let through: ${response.body}",
            )
            assertEquals(0, tool.calls, "A tool ran for a rebound host.")

            harness.controller.stop()
        }
    }

    /**
     * The control for both refusals above: a client on this machine, which sends the loopback host
     * and either no origin at all or a loopback one, is let through.
     *
     * Without this the two refusals would also be satisfied by a server that refuses everything.
     */
    @Test
    fun `a client on this machine is let through`() = runTest {
        val port = freePort()

        McpServerHarness(tools = tools()).use { harness ->
            harness.controller.setPort(port)
            harness.controller.setEnabled(true)
            val token = assertNotNull(harness.controller.token.value)

            val withoutOrigin = withContext(Dispatchers.IO) {
                McpConversation(port = port, token = token).initialize()
            }
            assertEquals(
                200,
                withoutOrigin.status,
                "A local client sending no origin was refused: ${withoutOrigin.body}",
            )

            val fromLoopback = withContext(Dispatchers.IO) {
                McpConversation(port = port, token = token, origin = "http://localhost:$port")
                    .initialize()
            }
            assertEquals(
                200,
                fromLoopback.status,
                "A local client sending a loopback origin was refused: ${fromLoopback.body}",
            )

            harness.controller.stop()
        }
    }
}
