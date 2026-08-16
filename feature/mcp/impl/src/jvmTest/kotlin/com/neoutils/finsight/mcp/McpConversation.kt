package com.neoutils.finsight.mcp

/**
 * A client that speaks just enough of the protocol to exercise a server: it initialises, says it is
 * ready, lists what is offered and calls one of them.
 *
 * It carries the session it was given from one request to the next, which is what makes a sequence
 * of calls one conversation rather than several.
 */
internal class McpConversation(
    private val port: Int,
    private val token: String?,
    private val origin: String? = null,
) {

    var sessionId: String? = null
        private set

    fun initialize(): RawHttp.Response {
        val response = send(
            """
            {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"$PROTOCOL_VERSION",
            "capabilities":{},"clientInfo":{"name":"finsight-test","version":"1"}}}
            """.trimIndent().replace("\n", ""),
        )
        response["mcp-session-id"]?.let { sessionId = it }
        return response
    }

    fun notifyInitialized(): RawHttp.Response =
        send("""{"jsonrpc":"2.0","method":"notifications/initialized"}""")

    fun listTools(): RawHttp.Response =
        send("""{"jsonrpc":"2.0","id":2,"method":"tools/list"}""")

    fun callTool(name: String, arguments: String = "{}"): RawHttp.Response =
        send("""{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"$name","arguments":$arguments}}""")

    /** Initialises and announces readiness, leaving the conversation ready to call tools. */
    fun open(): McpConversation = apply {
        initialize()
        notifyInitialized()
    }

    fun send(body: String): RawHttp.Response = RawHttp.post(
        port = port,
        body = body,
        token = token,
        origin = origin,
        sessionId = sessionId,
    )

    private companion object {
        const val PROTOCOL_VERSION = "2025-11-25"
    }
}
