package com.neoutils.finsight.mcp.contract

import com.neoutils.finsight.feature.mcp.api.McpPermission
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ToolRegistryTest {

    @Test
    fun `every announced name is prefixed`() {
        assertFailsWith<IllegalArgumentException> {
            ToolRegistry(listOf(tool(name = "list_transactions")))
        }

        val registry = ToolRegistry(listOf(tool(name = "finsight_list_transactions")))
        assertTrue(registry.tools.all { it.name.startsWith(TOOL_NAME_PREFIX) })
    }

    @Test
    fun `a name outside the recommended character set is refused`() {
        assertFailsWith<IllegalArgumentException> {
            ToolRegistry(listOf(tool(name = "finsight list transactions")))
        }
    }

    @Test
    fun `two tools cannot announce the same name`() {
        assertFailsWith<IllegalArgumentException> {
            ToolRegistry(listOf(tool(name = "finsight_list_accounts"), tool(name = "finsight_list_accounts")))
        }
    }

    @Test
    fun `no tool is announced without an output schema`() {
        assertFailsWith<IllegalArgumentException> {
            ToolRegistry(listOf(tool(outputSchema = JsonObject(emptyMap()))))
        }
    }

    @Test
    fun `no tool is announced without a description`() {
        assertFailsWith<IllegalArgumentException> {
            ToolRegistry(listOf(tool(description = " ")))
        }
    }

    @Test
    fun `a read-only tool cannot declare a write`() {
        assertFailsWith<IllegalArgumentException> {
            ToolAnnotations(readOnlyHint = true, destructiveHint = true)
        }
    }

    @Test
    fun `writing is derived from the annotation, so the two cannot drift apart`() {
        assertFalse(tool(annotations = ToolAnnotations(readOnlyHint = true)).isWrite)
        assertTrue(tool(annotations = ToolAnnotations(readOnlyHint = false)).isWrite)
    }

    @Test
    fun `read-only hides the writes and refuses them all the same`() {
        val read = tool(name = "finsight_list_transactions", annotations = ToolAnnotations(readOnlyHint = true))
        val write = tool(
            name = "finsight_record_transactions",
            annotations = ToolAnnotations(readOnlyHint = false, idempotentHint = true),
        )
        val registry = ToolRegistry(listOf(read, write))

        // Hiding is for the well-behaved client…
        assertEquals(listOf(read), registry.visibleTo(McpPermission.READ_ONLY))
        // …and refusing is what holds when one ignores the annotations.
        assertFalse(registry.isPermitted(write, McpPermission.READ_ONLY))
        assertTrue(registry.isPermitted(write, McpPermission.READ_WRITE))
        assertEquals(listOf(read, write), registry.visibleTo(McpPermission.READ_WRITE))
    }

    @Test
    fun `a name nobody announced resolves to nothing`() {
        val registry = ToolRegistry(listOf(tool(name = "finsight_list_accounts")))

        assertNull(registry.find("finsight_close_invoice"))
    }

    private fun tool(
        name: String = "finsight_list_transactions",
        description: String = "Lists transactions. Totals come from finsight_aggregate_transactions.",
        outputSchema: JsonObject = toolOutcomeSchema(
            resultSchema = buildJsonObject { put("type", "object") },
            errorCodes = setOf("INVALID_PERIOD"),
        ),
        annotations: ToolAnnotations = ToolAnnotations(readOnlyHint = true),
    ) = object : McpTool {
        override val name = name
        override val title = "Transactions"
        override val description = description
        override val inputSchema = buildJsonObject { put("type", "object") }
        override val outputSchema = outputSchema
        override val annotations = annotations
        override suspend fun execute(arguments: JsonObject) = ToolOutcome.Ok(buildJsonObject { })
    }
}
