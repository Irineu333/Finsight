package com.neoutils.finsight.mcp

import com.neoutils.finsight.database.DatabaseOwnership
import com.neoutils.finsight.feature.mcp.api.McpServerState
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
import kotlin.test.assertNotNull

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

    // Held rather than thrown, so that the hang-up below happens whatever the conversation did and
    // the session is asked the same question in either case.
    val conversation = runCatching {
        client.connect(
            StdioClientTransport(
                input = pipes.clientIn.asSource().buffered(),
                output = pipes.clientOut.asSink().buffered(),
            ),
        )
        block(client)
    }

    runCatching { client.close() }
    pipes.hangUp()
    // A limit rather than a plain join, so that a session which failed to notice the hang-up ends
    // the run instead of holding it — and the limit is *answered*, below, because
    // `withTimeoutOrNull` expiring is silent and a suite that cancelled its way past this would
    // prove nothing about a process ending with its client.
    val ended = withTimeoutOrNull(HANG_UP_MILLIS) { serving.join() }
    serving.cancel()
    pipes.close()

    // The conversation's own failure first, and before the assertion below: a test that failed on
    // what it came to assert must report that, not the session it then left behind.
    conversation.getOrThrow()

    assertNotNull(
        ended,
        "the session was still serving $HANG_UP_MILLIS ms after the client closed the input — a " +
            "stdio process that does not end with its client is one the client leaves running",
    )
}

/**
 * The session the desktop resolves, built from a harness's own archive, preferences and log.
 *
 * The ownership is derived from that archive and never from the default path, so a test claims
 * nothing beside the database of whoever is running it.
 */
internal fun McpServerHarness.stdioSession(
    ownership: DatabaseOwnership = DatabaseOwnership(databasePath),
    /**
     * What this process offers. The harness's own by default; a test asking which of the two
     * processes answered gives the headless one tools of the same names that say so.
     */
    tools: List<McpTool> = this.tools,
    bridge: McpBridge = McpBridge(serverSettings),
    diagnostics: PrintStream = PrintStream(OutputStream.nullOutputStream()),
) = DesktopMcpStdioSession(
    settings = serverSettings,
    journal = journal,
    tools = tools,
    ownership = ownership,
    bridge = bridge,
    diagnostics = diagnostics,
)

/**
 * The window of the app, as a headless session finds it: another process owning the archive, and
 * this app's own server listening at the address the preferences name.
 *
 * **The ownership has to be held by a process of its own.** A JDK file lock belongs to the whole
 * JVM, so a claim taken in the test would be handed straight back to the session under test, and
 * every forwarding test would prove the opposite of what it set out to.
 *
 * **The socket, on the other hand, is this process's own.** It is the very controller the window
 * runs, and having it here is what lets a test watch a `Flow` of the open app wake up when the
 * agent writes through the bridge.
 *
 * The switch has to be on before this is called: it is the window opening, not the user switching
 * the server on.
 */
internal suspend fun McpServerHarness.openTheWindow(): ArchiveHolder {
    val window = ArchiveHolder(databasePath)
    check(window.next() == HELD) { "the window did not take the ownership of the archive" }
    controller.start()
    check(controller.state.value is McpServerState.Running) {
        "the window did not bind its server: ${controller.state.value}"
    }
    return window
}

/** The user closing the app: the socket goes, and then the archive is let go of. */
internal suspend fun McpServerHarness.closeTheWindow(window: ArchiveHolder) {
    controller.stop()
    window.letGo()
    window.close()
}

/** How long a session is given to notice that the client hung up. */
internal const val HANG_UP_MILLIS = 10_000L
