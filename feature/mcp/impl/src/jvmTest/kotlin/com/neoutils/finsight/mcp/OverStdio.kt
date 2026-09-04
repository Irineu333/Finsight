package com.neoutils.finsight.mcp

import com.neoutils.finsight.database.DatabaseOwnership
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import java.io.InputStream
import java.io.OutputStream
import java.io.PrintStream
import java.nio.channels.Channels
import java.nio.channels.Pipe

/**
 * The two pipes a client and a stdio server hold between them, in this process.
 *
 * They are operating-system pipes and not an in-memory queue: what is being exercised is a
 * transport that frames messages on a byte stream and ends when the stream does, and both of those
 * are properties of a real pipe. `Pipe` rather than `PipedInputStream` because the latter refuses
 * to read once the thread that last wrote has finished — which, on a dispatcher that hands
 * coroutines whichever thread is free, is a failure that arrives at random.
 */
internal class StdioPipes : AutoCloseable {

    private val toServer: Pipe = Pipe.open()
    private val toClient: Pipe = Pipe.open()

    /** What the process was launched with: the client's questions arrive here. */
    val serverIn: InputStream = Channels.newInputStream(toServer.source())

    /** And its answers leave here — the stream that carries the protocol and nothing else. */
    val serverOut: OutputStream = Channels.newOutputStream(toClient.sink())

    val clientIn: InputStream = Channels.newInputStream(toClient.source())

    val clientOut: OutputStream = Channels.newOutputStream(toServer.sink())

    /** The client going away, which for a stdio session is the end of everything. */
    fun hangUp() {
        runCatching { toServer.sink().close() }
    }

    override fun close() {
        listOf(toServer.sink(), toServer.source(), toClient.sink(), toClient.source())
            .forEach { runCatching { it.close() } }
    }
}

/**
 * Runs a conversation against a **real stdio session** over those pipes, with the SDK's own client
 * on the other end.
 *
 * It is the counterpart of `overTheProtocol`, and it exists for the same reason: what is being
 * asserted — a handshake, a list, a refusal an agent has to read — only exists on the wire. The
 * session is the one the app resolves, the transport is the one the app binds, and the client is a
 * library that knows nothing of either.
 */
internal suspend fun DesktopMcpStdioSession.servedOverStdio(
    pipes: StdioPipes = StdioPipes(),
    /**
     * Whether the session serves the process's own streams — what the entry point calls, with no
     * stream stated — rather than the pipes named here. A caller that asks for it has already
     * pointed `System.in` and `System.out` at these very pipes, which is what the operating system
     * does for a launched process.
     */
    onTheProcessOwnStreams: Boolean = false,
    block: suspend (Client) -> Unit,
) = coroutineScope {
    val session = this@servedOverStdio
    val serving = launch(Dispatchers.IO) {
        if (onTheProcessOwnStreams) {
            session.serve()
        } else {
            session.serve(
                input = pipes.serverIn.asSource().buffered(),
                output = pipes.serverOut.asSink().buffered(),
            )
        }
    }

    val client = Client(Implementation(name = "finsight-stdio-test", version = "1"))
    try {
        client.connect(
            StdioClientTransport(
                input = pipes.clientIn.asSource().buffered(),
                output = pipes.clientOut.asSink().buffered(),
            ),
        )
        block(client)
    } finally {
        runCatching { client.close() }
        pipes.hangUp()
        // The session ends when the input does. A limit rather than a plain join so a session that
        // failed to notice fails the run instead of hanging it.
        withTimeoutOrNull(HANG_UP_MILLIS) { serving.join() }
        serving.cancel()
        pipes.close()
    }
}

/**
 * The session the desktop resolves, built from a harness's own archive, preferences and log.
 *
 * The ownership is derived from that archive and never from the default path, so a test claims
 * nothing beside the database of whoever is running it.
 */
internal fun McpServerHarness.stdioSession(
    ownership: DatabaseOwnership = DatabaseOwnership(databasePath),
    diagnostics: PrintStream = PrintStream(OutputStream.nullOutputStream()),
) = DesktopMcpStdioSession(
    settings = serverSettings,
    journal = journal,
    tools = tools,
    ownership = ownership,
    diagnostics = diagnostics,
)

/** How long the session is given to notice that the client hung up. */
private const val HANG_UP_MILLIS = 10_000L
