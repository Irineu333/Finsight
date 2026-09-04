package com.neoutils.finsight.mcp

import io.modelcontextprotocol.kotlin.sdk.shared.AbstractTransport
import io.modelcontextprotocol.kotlin.sdk.shared.TransportSendOptions
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCRequest
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCResponse
import io.modelcontextprotocol.kotlin.sdk.types.ListToolsResult
import io.modelcontextprotocol.kotlin.sdk.types.Method
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * **The first message of a session is answered by this app, and not by the SDK's empty registry.**
 *
 * `Server.createSession` installs handlers of its own for `tools/list` and `tools/call`, connects the
 * transport, and only then runs the `onConnect` callbacks where this assembly puts its own
 * (`Server.kt:209-271` of `kotlin-sdk-server:0.14.0`). Between the connect and the callbacks there is
 * an interval, and a message that arrives inside it is answered by the SDK — from the registry this
 * assembly deliberately leaves empty, because a registry answers `tools/list` without the permission
 * filter.
 *
 * The interval is short, so it looks like nothing. Measured against the packaged launcher it was
 * eleven empty first lists in twenty launches: a client that asks for the list the instant the
 * handshake is done sees an app that offers nothing, and a user sees a mute server.
 *
 * **It is provoked here rather than waited for.** The order is fixed — the transport is connected
 * before the callbacks run — so a transport that delivers its first message from inside `start()`
 * lands in the middle of the interval every single time. No sleeping, no repetition, no luck. Given
 * `createSession` directly, both of these read the SDK's empty registry; given `openSession`, which
 * is what the session the desktop resolves uses, the transport is not started until the interval is
 * over and the same first message is answered by this app.
 */
class TheFirstMessageOfASessionIsAnsweredByTheAssemblyTest {

    /**
     * The list, which is what a client asks for first and what the whole surface looks like to it.
     */
    @Test
    fun `a tools list arriving as the transport connects is answered with the tools`() = runBlocking {
        val tool = SpyTool(name = McpToolName.LIST_ACCOUNTS.wireName, effect = McpToolEffect.READS)

        McpServerHarness(tools = listOf(tool)).use { harness ->
            harness.serverSettings.setEnabled(true)

            val listed = harness.answerWhileConnecting(
                JSONRPCRequest(method = Method.Defined.ToolsList.value),
            ) { it.result as ListToolsResult }

            assertEquals(
                listOf(tool.name),
                listed.tools.map { it.name },
                "The first list of the session was answered by something other than this app, so " +
                    "a client that asks the instant it is connected reads an app that offers " +
                    "nothing.",
            )
        }
    }

    /**
     * And the call, which is the worse half.
     *
     * The SDK answers a name its registry does not hold with *"tool not found"* — said about an
     * operation this app has, to an agent that will repeat it to the user as something the app
     * cannot do. It is the one statement `McpPermissionNotice` exists to keep off this surface.
     */
    @Test
    fun `a tools call arriving as the transport connects reaches the tool`() = runBlocking {
        val tool = SpyTool(name = McpToolName.LIST_ACCOUNTS.wireName, effect = McpToolEffect.READS)

        McpServerHarness(tools = listOf(tool)).use { harness ->
            harness.serverSettings.setEnabled(true)

            val answer = harness.answerWhileConnecting(
                JSONRPCRequest(
                    method = Method.Defined.ToolsCall.value,
                    params = buildJsonObject { put("name", tool.name) },
                ),
            ) { it.result as CallToolResult }

            val text = (answer.content.single() as TextContent).text
            assertFalse(
                "not found" in text.lowercase(),
                "The first call of the session was told the operation does not exist, which is " +
                    "false about this app and is what an agent repeats to its owner: $text",
            )
            assertEquals("done", text, "The call did not reach the tool.")
            assertTrue(answer.isError != true, "The call came back as a refusal.")
            assertEquals(1, tool.calls, "The tool did not run for the call that arrived first.")
        }
    }
}

/**
 * Opens a session whose transport delivers [first] from inside `start()` — the middle of the
 * interval — and reads back what was answered.
 */
private suspend fun <T> McpServerHarness.answerWhileConnecting(
    first: JSONRPCRequest,
    read: (JSONRPCResponse) -> T,
): T {
    val transport = DeliversWhileConnecting(first)
    val server = McpSessionFactory(
        settings = serverSettings,
        journal = journal,
        tools = tools,
    ).openSession(transport)

    return try {
        read(transport.answer() as JSONRPCResponse)
    } finally {
        runCatching { server.close() }
    }
}

/**
 * A transport that hands the session its first message at the one moment the SDK is between
 * connecting it and letting this app install its handlers.
 *
 * `Protocol.connect` registers the message callback and then calls `start()`, and `onRequest` runs
 * the handler and sends the answer inline — so delivering from `start()` puts the whole exchange
 * inside the interval, deterministically.
 */
private class DeliversWhileConnecting(private val first: JSONRPCRequest) : AbstractTransport() {

    private val sent = CompletableDeferred<JSONRPCMessage>()

    override suspend fun start() {
        _onMessage(first)
    }

    override suspend fun send(message: JSONRPCMessage, options: TransportSendOptions?) {
        sent.complete(message)
    }

    override suspend fun close() = invokeOnCloseCallback()

    /** What the session answered — with a limit, so a session that answers nothing fails the run. */
    suspend fun answer(): JSONRPCMessage = withTimeout(ANSWER_TIMEOUT_MILLIS) { sent.await() }

    private companion object {
        const val ANSWER_TIMEOUT_MILLIS = 10_000L
    }
}
