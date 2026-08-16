package com.neoutils.finsight.mcp

import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.JsonObject
import java.util.concurrent.atomic.AtomicInteger

/**
 * A tool that counts how often it was actually run.
 *
 * The count is the instrument of every "nothing was executed" claim in this suite: a refusal that
 * happens after the tool ran is not a refusal, and only the tool itself can say which it was.
 */
internal class SpyTool(
    override val name: String,
    override val effect: McpToolEffect,
    override val description: String = "A tool that exists so the server has something to offer.",
    override val inputSchema: ToolSchema = ToolSchema(),
    private val answer: suspend (JsonObject?) -> McpToolResult = { McpToolResult(text = "done") },
) : McpTool {

    private val runs = AtomicInteger()

    /** How many times the body actually ran. */
    val calls: Int get() = runs.get()

    override suspend fun call(arguments: JsonObject?): McpToolResult {
        runs.incrementAndGet()
        return answer(arguments)
    }
}
