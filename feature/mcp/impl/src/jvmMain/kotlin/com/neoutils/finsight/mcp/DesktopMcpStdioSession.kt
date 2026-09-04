package com.neoutils.finsight.mcp

import com.neoutils.finsight.database.DatabaseOwnership
import com.neoutils.finsight.feature.mcp.api.McpStdioSession
import com.neoutils.finsight.feature.mcp.api.McpStdout
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import java.io.PrintStream

/**
 * The desktop stdio session: one conversation, on the streams the process was launched with, for as
 * long as the client keeps them open.
 *
 * **It is the same server as the window's, over a different pipe.** The tools, the permission filter
 * on what is announced and on what runs, the journal every call passes through and the instructions
 * of the handshake all come from [McpSessionFactory], so the two modes cannot answer differently for
 * the same permissions (design D8). What this adds is what only a process without a window has: the
 * database is not its own, so every call takes the ownership before it runs and gives it back after
 * (design D3, D4), and the app's switch has to be honoured by a process that speaks even when it is
 * off (design D7) — which is a different server altogether, assembled next door.
 *
 * **One server per process, and no lifecycle.** There is nothing to start, nothing to stop and
 * nothing to reconnect to: the session begins when the process does and ends when the client closes
 * the input, which is also when the process has nothing left to do.
 */
internal class DesktopMcpStdioSession(
    private val settings: McpServerSettings,
    private val journal: AgentActivityJournal,
    private val tools: List<McpTool>,
    private val ownership: DatabaseOwnership,
    /**
     * Where the process says what it is. Never the standard output, which carries the protocol and
     * nothing else (design D6) — clients display this stream, and it is the only place a session
     * that answers nothing can explain itself.
     */
    private val diagnostics: PrintStream = System.err,
) : McpStdioSession {

    override suspend fun serve() = serve(
        input = System.`in`.asSource().buffered(),
        // From the claim and never from `System.out`, which by now is the diagnostics stream.
        output = McpStdout.protocol.asSink().buffered(),
    )

    /**
     * Serves the conversation on the given streams, which is what the process's own are.
     *
     * Stated separately so a test can put a pipe where the process's streams would be and hold a
     * whole conversation over it — the protocol is the subject, and a test that could only reach it
     * through the real standard output would be testing the launcher instead.
     */
    internal suspend fun serve(input: Source, output: Sink) {
        val enabled = settings.isEnabled.value
        announce(enabled)

        val server = if (enabled) offering() else McpServerOff.newServer()
        val transport = StdioServerTransport(input = input, output = output)

        // Completed when the client closes the input, which the transport reports the same way for
        // an end of stream and for a close of its own.
        val ended = CompletableDeferred<Unit>()
        transport.onClose { ended.complete(Unit) }

        try {
            server.createSession(transport)
            ended.await()
        } finally {
            withContext(NonCancellable) { runCatching { server.close() } }
        }
    }

    /** The assembly the window uses, with this process's rule about who may execute added to it. */
    private fun offering(): Server = McpSessionFactory(
        settings = settings,
        journal = journal,
        tools = tools,
        calls = McpStdioCallSite(ownership),
    ).newServer()

    /**
     * The opening line: which build answered, in which mode, and whether the app is offering
     * anything at all.
     *
     * The three facts a client's log has to carry for a session that went wrong to be explainable —
     * a stale path in a configuration answers with the version it actually launched, and a session
     * that lists nothing says why on the line above.
     */
    private fun announce(enabled: Boolean) {
        val state = if (enabled) "server enabled" else "server switched off in the app"
        diagnostics.println("finsight ${version()} — mcp stdio mode — $state")
        diagnostics.flush()
    }

    /**
     * The version of the launcher this process was started from.
     *
     * `jpackage` writes `-Djpackage.app-version=<version>` into every launcher it produces, which is
     * how the backup vault already stamps a copy with the build that took it. A run started from
     * Gradle has no launcher and says so rather than inventing a number.
     */
    private fun version(): String =
        System.getProperty(PACKAGED_VERSION)?.takeIf { it.isNotBlank() } ?: "development build"

    private companion object {
        const val PACKAGED_VERSION = "jpackage.app-version"
    }
}
