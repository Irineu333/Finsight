package com.neoutils.finsight.mcp

import com.neoutils.finsight.mcp.McpServerHarness.Companion.freePort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.test.assertNotNull

/**
 * Runs a conversation against a **real server** holding this world's tools: bound to loopback,
 * behind its token, past the handshake.
 *
 * Everything the questions family is asserted on goes through here rather than through the tools'
 * Kotlin functions. The difference is not ceremony: the wire is where the argument schema is
 * enforced, where the payload is serialised, and where a refusal has to arrive flagged as an error
 * the agent must read. A tool that answered correctly in Kotlin and nowhere else would pass a
 * direct call and fail a client.
 */
internal suspend fun AgentWorld.overTheProtocol(block: suspend (McpConversation) -> Unit) {
    val port = freePort()

    McpServerHarness(tools = tools()).use { harness ->
        harness.controller.setPort(port)
        harness.controller.setEnabled(true)
        val token = assertNotNull(harness.controller.token.value, "the server minted no token")

        withContext(Dispatchers.IO) {
            block(McpConversation(port, token).open())
        }

        harness.controller.stop()
    }
}
