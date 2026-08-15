@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.mcp.write

import com.neoutils.finsight.feature.mcp.api.AgentActivity
import com.neoutils.finsight.feature.mcp.api.AgentActivityOutcome
import com.neoutils.finsight.feature.mcp.api.IAgentActivityRepository
import com.neoutils.finsight.mcp.FixedClock
import com.neoutils.finsight.mcp.contract.McpTool
import com.neoutils.finsight.mcp.contract.ToolAnnotations
import com.neoutils.finsight.mcp.contract.ToolError
import com.neoutils.finsight.mcp.contract.ToolOutcome
import com.neoutils.finsight.mcp.contract.toolOutcomeSchema
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class ActivityRecorderTest {

    private val now = Instant.parse("2026-03-01T10:00:00Z")

    private val arguments = json("""{"idempotencyKey":"batch-1","items":[{"intent":"EXPENSE"}]}""")

    @Test
    fun `a read produces no record at all`() = runTest {
        val journal = FakeJournal()
        recorder(journal).record(tool("finsight_list_transactions", writes = false), arguments, ToolOutcome.Ok(json("{}")))

        assertTrue(journal.records.isEmpty())
    }

    @Test
    fun `one call leaves one record, however many rows it wrote`() = runTest {
        val journal = FakeJournal()

        recorder(journal).record(
            tool = tool("finsight_record_transactions", writes = true),
            arguments = arguments,
            outcome = ToolOutcome.Ok(json("{}")),
            affected = listOf("transaction:1", "transaction:2", "transaction:3"),
        )

        val record = journal.records.single()
        assertEquals(AgentActivityOutcome.OK, record.outcome)
        assertEquals(listOf("transaction:1", "transaction:2", "transaction:3"), record.affected)
        assertEquals(now, record.timestamp)
        assertEquals("finsight_record_transactions", record.tool)
    }

    @Test
    fun `a refusal is recorded too, and is told apart from a failure`() = runTest {
        val journal = FakeJournal()
        val recorder = recorder(journal)
        val writer = tool("finsight_record_transactions", writes = true)

        recorder.record(writer, arguments, ToolOutcome.Failed(ToolError.domainRule("CLOSED_INVOICE", "closed")))
        recorder.record(writer, arguments, ToolOutcome.Failed(ToolError.permission("PERMISSION_READ_ONLY", "read-only")))
        recorder.record(writer, arguments, ToolOutcome.Failed(ToolError.internal("BROKE", "it broke")))

        assertEquals(
            listOf(AgentActivityOutcome.REFUSED, AgentActivityOutcome.REFUSED, AgentActivityOutcome.FAILED),
            journal.records.map { it.outcome },
        )
    }

    @Test
    fun `the token appears in no field of the record`() = runTest {
        val journal = FakeJournal()

        recorder(journal, client = { "claude-code" }).record(
            tool = tool("finsight_record_transactions", writes = true),
            arguments = arguments,
            outcome = ToolOutcome.Ok(json("{}")),
        )

        val record = journal.records.single()
        val everyField = listOf(record.tool, record.arguments, record.client.orEmpty(), record.affected.toString())
        everyField.forEach { field ->
            assertFalse(field.contains(TOKEN), "The token reached `$field`")
            assertFalse(field.contains("Bearer", ignoreCase = true), "An authorization header reached `$field`")
        }
    }

    @Test
    fun `the arguments are recorded as received`() = runTest {
        val journal = FakeJournal()

        recorder(journal).record(
            tool = tool("finsight_record_transactions", writes = true),
            arguments = arguments,
            outcome = ToolOutcome.Ok(json("{}")),
        )

        assertEquals(arguments.toString(), journal.records.single().arguments)
    }

    @Test
    fun `a client that never introduced itself is recorded as unknown, and nothing fails`() = runTest {
        val journal = FakeJournal()

        recorder(journal, client = { null }).record(
            tool = tool("finsight_record_transactions", writes = true),
            arguments = arguments,
            outcome = ToolOutcome.Ok(json("{}")),
        )

        assertNull(journal.records.single().client)
    }

    @Test
    fun `retention is declared, and pruning applies it`() = runTest {
        val journal = FakeJournal()
        val recorder = recorder(journal)

        assertEquals(90.days, ActivityRecorder.DEFAULT_RETENTION)

        recorder.prune()
        assertEquals(now - 90.days, journal.pruned)
    }

    private fun recorder(
        journal: IAgentActivityRepository,
        client: () -> String? = { "claude-code" },
    ) = ActivityRecorder(journal = journal, clock = FixedClock(now), declaredClient = client)

    private fun json(raw: String): JsonObject = Json.parseToJsonElement(raw) as JsonObject

    private fun tool(name: String, writes: Boolean) = object : McpTool {
        override val name: String = name
        override val title: String = name
        override val description: String = "A tool the test observes the journal through."
        override val inputSchema: JsonObject = json("""{"type":"object"}""")
        override val outputSchema: JsonObject = toolOutcomeSchema(inputSchema, setOf("BROKEN"))
        override val annotations = ToolAnnotations(readOnlyHint = !writes)
        override suspend fun execute(arguments: JsonObject): ToolOutcome = ToolOutcome.Ok(json("{}"))
    }

    private companion object {
        /** What authenticates a request. It travels in a header and reaches no tool. */
        const val TOKEN = "the-bearer-token"
    }
}

private class FakeJournal : IAgentActivityRepository {

    val records: MutableList<AgentActivity> = mutableListOf()

    var pruned: Instant? = null
        private set

    private val recent = MutableStateFlow<List<AgentActivity>>(emptyList())

    override fun observeRecent(limit: Int): Flow<List<AgentActivity>> = recent

    override suspend fun record(activity: AgentActivity) {
        records += activity
        recent.value = records.toList()
    }

    override suspend fun prune(olderThan: Instant) {
        pruned = olderThan
    }
}
