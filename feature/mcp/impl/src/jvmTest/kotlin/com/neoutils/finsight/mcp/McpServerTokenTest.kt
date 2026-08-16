package com.neoutils.finsight.mcp

import com.neoutils.finsight.feature.mcp.api.AgentActivity
import com.neoutils.finsight.feature.mcp.api.McpServerState
import com.neoutils.finsight.mcp.McpServerHarness.Companion.freePort
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The token: that a request without one executes nothing, that a client configured once keeps
 * working across restarts, and that minting a new one ends the old one.
 */
class McpServerTokenTest {

    private fun writingTool() = SpyTool(
        name = "record_expense",
        effect = McpToolEffect.CHANGES,
        answer = {
            McpToolResult(
                text = "Recorded.",
                outcome = AgentActivity.Outcome.APPLIED,
                summary = "Coffee on Checking",
                reference = AgentActivity.Reference(AgentActivity.Reference.Kind.TRANSACTION, 1),
            )
        },
    )

    /**
     * The refusal happens before the transport, before the session and before the tool — which is
     * what "nothing is executed" has to mean. The tool's own counter is the witness, and the
     * session count is the second: an unauthorised caller never gets one.
     */
    @Test
    fun `a request with no token executes nothing`() = runTest {
        val tool = writingTool()
        val port = freePort()

        McpServerHarness(tools = listOf(tool)).use { harness ->
            harness.controller.setPort(port)
            harness.controller.setEnabled(true)

            val response = withContext(Dispatchers.IO) {
                McpConversation(port = port, token = null).initialize()
            }

            assertEquals(401, response.status, "A request with no token was not refused.")
            assertEquals(0, tool.calls, "A tool ran for a caller that presented no token.")
            assertEquals(
                McpServerState.Running(port = port, sessions = 0),
                harness.controller.state.value,
                "An unauthorised caller was given a session.",
            )

            harness.controller.stop()
        }
    }

    /** A token that does not match is the same as none: nothing behind it runs. */
    @Test
    fun `a request with the wrong token executes nothing`() = runTest {
        val tool = writingTool()
        val port = freePort()

        McpServerHarness(tools = listOf(tool)).use { harness ->
            harness.controller.setPort(port)
            harness.controller.setEnabled(true)

            val response = withContext(Dispatchers.IO) {
                McpConversation(port = port, token = "not-the-token").initialize()
            }

            assertEquals(401, response.status, "A request with a wrong token was not refused.")
            assertEquals(0, tool.calls, "A tool ran for a caller whose token did not match.")
            assertEquals(
                McpServerState.Running(port = port, sessions = 0),
                harness.controller.state.value,
            )

            harness.controller.stop()
        }
    }

    /** The token that was minted is the one that works, and it opens a session. */
    @Test
    fun `the minted token is accepted and opens a session`() = runTest {
        val tool = writingTool()
        val port = freePort()

        McpServerHarness(tools = listOf(tool)).use { harness ->
            harness.controller.setPort(port)
            harness.controller.setEnabled(true)

            val token = assertNotNull(
                harness.controller.token.value,
                "No token was minted for a server that came up.",
            )

            val response = withContext(Dispatchers.IO) {
                McpConversation(port = port, token = token).initialize()
            }

            assertEquals(200, response.status, "The minted token was not accepted: ${response.body}")
            assertNotNull(response["mcp-session-id"], "No session was opened: ${response.body}")

            harness.controller.stop()
        }
    }

    /**
     * The address configured in a client has to keep working: the app closing and opening again is
     * not a reason to reconfigure anything.
     */
    @Test
    fun `the token survives the app closing and opening again`() = runTest {
        val settings = MapSettings()
        val port = freePort()
        val firstToken: String

        McpServerHarness(settings).use { firstRun ->
            firstRun.controller.setPort(port)
            firstRun.controller.setEnabled(true)
            firstToken = assertNotNull(firstRun.controller.token.value)
            firstRun.controller.stop()
        }

        McpServerHarness(settings).use { secondRun ->
            secondRun.controller.start()

            assertEquals(
                firstToken,
                secondRun.controller.token.value,
                "The token changed across a restart, so every configured client is now wrong.",
            )

            val response = withContext(Dispatchers.IO) {
                McpConversation(port = port, token = firstToken).initialize()
            }

            assertEquals(
                200,
                response.status,
                "A client configured before the restart was refused after it: ${response.body}",
            )

            secondRun.controller.stop()
        }
    }

    /** Minting a new one is how the user revokes: whoever held the old one is out. */
    @Test
    fun `regenerating the token stops the previous one from working`() = runTest {
        val port = freePort()

        McpServerHarness().use { harness ->
            harness.controller.setPort(port)
            harness.controller.setEnabled(true)
            val previous = assertNotNull(harness.controller.token.value)

            harness.controller.regenerateToken()
            val current = assertNotNull(harness.controller.token.value)

            assertTrue(previous != current, "Regenerating produced the same token.")
            assertEquals(
                McpServerState.Running(port = port, sessions = 0),
                harness.controller.state.value,
                "The server did not stay up across the token being replaced.",
            )

            val refused = withContext(Dispatchers.IO) {
                McpConversation(port = port, token = previous).initialize()
            }
            assertEquals(401, refused.status, "The previous token is still accepted.")

            val accepted = withContext(Dispatchers.IO) {
                McpConversation(port = port, token = current).initialize()
            }
            assertEquals(200, accepted.status, "The new token was not accepted: ${accepted.body}")

            harness.controller.stop()
        }
    }
}
