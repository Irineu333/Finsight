package com.neoutils.finsight.mcp

import com.neoutils.finsight.feature.mcp.api.AgentActivity
import com.neoutils.finsight.mcp.McpServerHarness.Companion.freePort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * What an agent leaves behind, driven over the wire: the questions leave nothing, and everything
 * that changed something — or was refused while trying to — leaves exactly one entry.
 *
 * The log is the only place the **authorship** of a write appears. Reactivity delivers the result
 * and says nothing about where it came from, so without this a posting an agent should never have
 * made is indistinguishable from one the user forgot making.
 */
class AgentActivityIsOnlyWhatChangedTest {

    private val reader = SpyTool(
        name = "get_month_summary",
        effect = McpToolEffect.READS,
        answer = { McpToolResult(text = "Spent 120,00 in March.") },
    )

    private val writer = SpyTool(
        name = "record_expense",
        effect = McpToolEffect.CHANGES,
        answer = {
            McpToolResult(
                text = "Recorded.",
                outcome = AgentActivity.Outcome.APPLIED,
                summary = "Coffee, 12,00, on Checking",
                reference = AgentActivity.Reference(AgentActivity.Reference.Kind.TRANSACTION, 7),
            )
        },
    )

    private val refuser = SpyTool(
        name = "delete_transaction",
        effect = McpToolEffect.CHANGES,
        answer = {
            McpToolResult(
                text = "Deleting is not permitted.",
                outcome = AgentActivity.Outcome.REFUSED,
                summary = "Coffee, 12,00, on Checking",
                detail = "The deleting axis is not granted",
            )
        },
    )

    /**
     * An agent asks dozens of questions to answer one. Listing them would bury the handful of acts
     * that actually changed something, and a read has nothing to audit in the first place.
     */
    @Test
    fun `a run of questions leaves the log empty`() = runTest {
        val port = freePort()

        McpServerHarness(tools = listOf(reader, writer)).use { harness ->
            harness.controller.setPort(port)
            harness.controller.setEnabled(true)
            val token = assertNotNull(harness.controller.token.value)

            withContext(Dispatchers.IO) {
                val client = McpConversation(port, token).open()
                client.listTools()
                repeat(times = 3) { client.callTool(reader.name) }
            }

            assertEquals(3, reader.calls, "The questions did not reach the tool.")
            assertEquals(
                emptyList(),
                harness.activity.observeAll().first(),
                "A question was written to the log.",
            )

            harness.controller.stop()
        }
    }

    /**
     * Nothing de-duplicates, on purpose. A repeated call is a second act, and the duplication that
     * a missing idempotency permits is exactly what the log exists to expose: the two identical
     * postings sit side by side, with their times.
     */
    @Test
    fun `the same write twice leaves two entries side by side`() = runTest {
        val port = freePort()

        McpServerHarness(tools = listOf(writer)).use { harness ->
            harness.controller.setPort(port)
            harness.controller.setEnabled(true)
            val token = assertNotNull(harness.controller.token.value)

            withContext(Dispatchers.IO) {
                val client = McpConversation(port, token).open()
                client.callTool(writer.name, arguments = """{"amount":12.0}""")
                client.callTool(writer.name, arguments = """{"amount":12.0}""")
            }

            val log = harness.activity.observeAll().first()

            assertEquals(2, log.size, "A repeated write was collapsed into one entry.")
            assertTrue(
                log.all { it.operation == writer.name && it.summary == "Coffee, 12,00, on Checking" },
                "The two entries do not describe the same act: $log",
            )
            assertTrue(
                log.all { it.outcome == AgentActivity.Outcome.APPLIED },
                "A write that went through was not recorded as applied: $log",
            )
            assertTrue(
                log.all { it.reference == AgentActivity.Reference(AgentActivity.Reference.Kind.TRANSACTION, 7) },
                "The entries do not reach what they produced: $log",
            )
            assertTrue(
                log.map { it.id }.distinct().size == 2,
                "The two acts are one row: $log",
            )
        }
    }

    /** A refusal is what explains to the user why the agent said it could not do something. */
    @Test
    fun `a refused operation is recorded with its reason`() = runTest {
        val port = freePort()

        McpServerHarness(tools = listOf(refuser)).use { harness ->
            harness.controller.setPort(port)
            harness.controller.setEnabled(true)
            val token = assertNotNull(harness.controller.token.value)

            val response = withContext(Dispatchers.IO) {
                McpConversation(port, token).open().callTool(refuser.name)
            }

            val log = harness.activity.observeAll().first()

            assertEquals(1, log.size, "The refusal left no trace: $log")
            assertEquals(AgentActivity.Outcome.REFUSED, log.single().outcome)
            assertEquals("The deleting axis is not granted", log.single().detail)
            assertTrue(
                """"isError":true""" in response.body.replace(" ", ""),
                "The agent was not told the call failed, so it cannot change course: ${response.body}",
            )

            harness.controller.stop()
        }
    }

    /**
     * A tool that throws instead of refusing is a defect, and a defect that vanished from the log
     * would leave the user's question — why did the agent say it could not do it — unanswered.
     */
    @Test
    fun `a write that throws is still recorded`() = runTest {
        val port = freePort()
        val thrower = SpyTool(
            name = "adjust_balance",
            effect = McpToolEffect.CHANGES,
            answer = { error("the ledger refused to balance") },
        )

        McpServerHarness(tools = listOf(thrower)).use { harness ->
            harness.controller.setPort(port)
            harness.controller.setEnabled(true)
            val token = assertNotNull(harness.controller.token.value)

            withContext(Dispatchers.IO) {
                McpConversation(port, token).open().callTool(thrower.name)
            }

            val log = harness.activity.observeAll().first()

            assertEquals(1, log.size, "A write that threw left no trace: $log")
            assertEquals(AgentActivity.Outcome.REFUSED, log.single().outcome)
            assertEquals("the ledger refused to balance", log.single().detail)

            harness.controller.stop()
        }
    }

    /** A question that fails is still a question, and the rule is the tool and never the outcome. */
    @Test
    fun `a question that fails is still not recorded`() = runTest {
        val port = freePort()
        val failingReader = SpyTool(
            name = "get_balance",
            effect = McpToolEffect.READS,
            answer = { error("no such account") },
        )

        McpServerHarness(tools = listOf(failingReader)).use { harness ->
            harness.controller.setPort(port)
            harness.controller.setEnabled(true)
            val token = assertNotNull(harness.controller.token.value)

            withContext(Dispatchers.IO) {
                McpConversation(port, token).open().callTool(failingReader.name)
            }

            assertEquals(
                emptyList(),
                harness.activity.observeAll().first(),
                "A failed question was written to the log.",
            )

            harness.controller.stop()
        }
    }
}
