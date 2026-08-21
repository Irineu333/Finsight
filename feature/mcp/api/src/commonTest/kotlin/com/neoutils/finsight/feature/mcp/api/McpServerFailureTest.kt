package com.neoutils.finsight.feature.mcp.api

import com.neoutils.finsight.util.UiText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * What a failure says to the user.
 *
 * The failure the user can act on is a port another program is holding, and the only thing that
 * makes it actionable is *which* port. A message that named no port would satisfy "the failure was
 * announced" and leave the user with nothing to do about it.
 */
class McpServerFailureTest {

    @Test
    fun `the message names the port that stopped the server`() {
        McpServerFailure.entries.forEach { cause ->
            val text = McpServerState.Failed(port = 8477, cause = cause).toUiText()

            val withArgs = assertIs<UiText.ResWithArgs>(
                text,
                "The message for $cause takes no arguments, so it cannot name a port.",
            )
            assertEquals(
                listOf(8477),
                withArgs.args,
                "The message for $cause does not carry the port that failed.",
            )
        }
    }

    @Test
    fun `each cause says something of its own`() {
        val messages = McpServerFailure.entries.map { cause ->
            (McpServerState.Failed(port = 1, cause = cause).toUiText() as UiText.ResWithArgs).res
        }

        assertEquals(
            McpServerFailure.entries.size,
            messages.distinct().size,
            "Two causes read the same, so the user cannot tell which one happened.",
        )
        assertTrue(
            McpServerFailure.entries.all { it.message.isNotBlank() },
            "A cause with no message is invisible in a log.",
        )
    }
}
