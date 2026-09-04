package com.neoutils.finsight.mcp

import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **A headless process runs nothing it is not the owner of the archive for.**
 *
 * The window takes the ownership before it opens the database and keeps it until it closes, so a
 * claim that is refused means one thing: somebody else is the database's process right now. What
 * this asks is that the session then executes *nothing* — not the tool, and not the row the journal
 * would have written, which is a row of that same archive.
 *
 * The decision is taken per call and never per session (design D3), and the second test is what
 * makes that a fact rather than a claim: the same conversation, the same client, and the answer
 * changes the moment the other process lets go.
 *
 * What this session answers *instead* is deliberately not asserted in words here: it is the
 * placeholder for the forwarding that belongs to the bridge, and the requirement it stands in for
 * is only that nothing ran locally.
 */
class TheStdioModeExecutesOnlyUnderTheOwnershipTest {

    @Test
    fun `a call is not carried out while another process owns the archive`() = runBlocking {
        val tool = SpyTool(name = McpToolName.CREATE_TRANSACTION.wireName, effect = McpToolEffect.CHANGES)

        McpServerHarness(tools = listOf(tool)).use { harness ->
            harness.serverSettings.setEnabled(true)

            ArchiveHolder(harness.databasePath).use { holder ->
                assertEquals(HELD, holder.next(), "the other process did not take the ownership")

                harness.stdioSession().servedOverStdio { client ->
                    val answer = client.callTool(tool.name, emptyMap())

                    assertTrue(
                        answer.isError ?: false,
                        "The call came back as though it had been carried out.",
                    )
                    assertContains(
                        (answer.content.single() as TextContent).text,
                        "open",
                        message = "The refusal was about something other than the app being open, " +
                            "so this test is passing for a reason it did not ask about.",
                    )
                    assertEquals(
                        0,
                        tool.calls,
                        "The tool ran while another process owned the archive.",
                    )
                }
            }

            assertTrue(
                harness.activity.observeAll().first().isEmpty(),
                "A process that may not touch the archive wrote a row into it.",
            )
        }
    }

    @Test
    fun `the next call in the same session is carried out once the other process lets go`() =
        runBlocking {
            val tool = SpyTool(
                name = McpToolName.CREATE_TRANSACTION.wireName,
                effect = McpToolEffect.CHANGES,
                answer = { McpToolResult(text = "Recorded.", summary = "one posting") },
            )

            McpServerHarness(tools = listOf(tool)).use { harness ->
                harness.serverSettings.setEnabled(true)

                ArchiveHolder(harness.databasePath).use { holder ->
                    assertEquals(HELD, holder.next(), "the other process did not take the ownership")

                    harness.stdioSession().servedOverStdio { client ->
                        assertTrue(
                            client.callTool(tool.name, emptyMap()).isError ?: false,
                            "the first call was carried out while the archive was another's",
                        )

                        holder.letGo()

                        val answer = client.callTool(tool.name, emptyMap())
                        assertEquals(
                            "Recorded.",
                            (answer.content.single() as TextContent).text,
                            "The call after the archive was let go was not carried out.",
                        )
                        assertEquals(1, tool.calls, "The tool ran a number of times it was not called.")
                    }
                }

                assertEquals(
                    1,
                    harness.activity.observeAll().first().size,
                    "The log kept something other than the one act that happened.",
                )
            }
        }
}
