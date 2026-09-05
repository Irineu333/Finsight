package com.neoutils.finsight.mcp

import com.neoutils.finsight.feature.mcp.api.AgentActivity
import com.neoutils.finsight.mcp.surface.AgentRefusal
import com.neoutils.finsight.mcp.tool.agentJson
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.sse.SSE
import io.ktor.http.HttpHeaders
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.server.ServerSession
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ListToolsResult
import io.modelcontextprotocol.kotlin.sdk.types.Method
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolListChangedNotification
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * The way from a headless session to the window's own server, taken whenever the window is the one
 * that owns the database.
 *
 * **It forwards the protocol, not the tools.** What crosses is a `tools/call` and a `tools/list` as
 * they arrived, and what comes back is what the window answered — the permissions it applies, the
 * refusals it words, the rows it writes to the activity log. Nothing of the fifty-odd tools is
 * decided a second time here, which is what keeps the two modes from drifting: with the window
 * open the client is talking to the window, through a pipe instead of a socket (design D8).
 *
 * **The conversation lasts as long as the window does, not as long as a call.** A client that
 * connected per call would never be listening at the moment the user moves a permission switch, and
 * `notifications/tools/list_changed` has no replay — so the session with the window is opened at the
 * first request that needs it and kept, and the announcement heard on it is repeated to the client
 * on the other side of this process. It is dropped the moment the window stops answering, and the
 * next request finds the database free and is answered here.
 *
 * **A closed port means a window on its way up** (design D10). It is reached for only once the
 * database is another process's, and the app's switch has already been read by the site that
 * consults this — so what is left is a window that has taken the archive and not yet bound its
 * socket. It is worth waiting [WINDOW_IS_STARTING_LIMIT] for; past that the client is told to make
 * the call again, rather than left with an act that silently did not happen.
 */
internal class McpBridge(
    private val settings: McpServerSettings,
    /**
     * How long a call waits for a window that owns the database and is not listening yet.
     */
    private val startingLimit: Duration = WINDOW_IS_STARTING_LIMIT,
) : McpCallSite {

    /**
     * The engine under the client, built on first use.
     *
     * A stdio process whose window is closed never forwards anything, and it is the common case:
     * the threads an HTTP engine brings up are spent only once there is something to say.
     */
    private val engine = lazy { HttpClient(OkHttp) { install(SSE) } }

    /** So that two requests arriving together open one conversation rather than two. */
    private val opening = Mutex()

    /**
     * The conversation with the window, while there is one.
     *
     * An atomic reference rather than a field under [opening], because it is cleared from the
     * transport's own callback when the window goes away, and that callback is not a suspending
     * context.
     */
    private val window = AtomicReference<Client?>(null)

    /**
     * The client on the other side of this process, where an announcement heard from the window is
     * repeated.
     */
    @Volatile
    private var listener: ServerSession? = null

    /**
     * The scope the repeated announcements are sent on.
     *
     * A notification handler answers with a [kotlinx.coroutines.Deferred] rather than suspending,
     * and writing to the client's stream is not the handler's thread's business. A failure to reach
     * a client that has already gone is nothing to report: the choice is persisted either way, and
     * the next list reads it.
     */
    private val announcing = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, _ -> },
    )

    /** Where an announcement from the window is repeated to, for as long as that session lasts. */
    fun relayTo(session: ServerSession) {
        listener = session
        session.onClose { listener = null }
    }

    override suspend fun answer(
        name: String,
        arguments: JsonObject?,
        here: suspend () -> McpToolResult,
    ): McpToolResult = when (val found = reach(waiting = true)) {
        is TheWindow.Answering -> forward(found.client, name, arguments)
        TheWindow.StillOpening -> refusal(STILL_OPENING)
    }

    /**
     * The window's list, and this process's own when there is no window answering.
     *
     * **A list never waits.** It is the first thing a client asks and it is answerable from here —
     * the same tools, filtered by the same axes — whereas an act is not, because the process that
     * owns the database is the only one that may carry it out. So a window that is still opening is
     * listed from this process, and the client is told the list changed the moment the window is
     * reached (design D10).
     */
    override suspend fun list(here: suspend () -> ListToolsResult): ListToolsResult =
        when (val found = reach(waiting = false)) {
            is TheWindow.Answering -> try {
                found.client.listTools()
            } catch (cause: Throwable) {
                currentCoroutineContext().ensureActive()
                hangUp()
                here()
            }

            TheWindow.StillOpening -> here()
        }

    /** Ends the conversation with the window and gives back what holding it cost. */
    suspend fun close() {
        announcing.cancel()
        listener = null
        window.getAndSet(null)?.let { runCatching { it.close() } }
        if (engine.isInitialized()) runCatching { engine.value.close() }
    }

    /**
     * Hands the call to the window and brings the answer back as this surface's own shape.
     *
     * A failure here is the window going away mid-call — the user closed it, which they are free to
     * do at any moment. The client is told, because a call whose fate is unknown must not read as
     * one that was applied, and the conversation is dropped so that the next request is decided
     * afresh (design D10).
     */
    private suspend fun forward(
        client: Client,
        name: String,
        arguments: JsonObject?,
    ): McpToolResult = try {
        client.callTool(
            CallToolRequest(CallToolRequestParams(name = name, arguments = arguments)),
        ).asToolResult()
    } catch (cause: Throwable) {
        currentCoroutineContext().ensureActive()
        hangUp()
        refusal(WINDOW_WENT_AWAY)
    }

    /**
     * What the window answered, in the shape this surface speaks.
     *
     * Every tool of this app answers with one piece of text, and it is that text the client is
     * given back; [CallToolResult.isError] carries which kind of answer it was, and it is the only
     * thing the assembly on this side reads the outcome for — the row in the activity log was
     * written by the window, which is the process that did the work.
     */
    private fun CallToolResult.asToolResult() = McpToolResult(
        text = content.filterIsInstance<TextContent>().joinToString(separator = "\n") { it.text },
        outcome = if (isError == true) {
            AgentActivity.Outcome.REFUSED
        } else {
            AgentActivity.Outcome.APPLIED
        },
    )

    /**
     * What is on the other end at this instant, and how long it is worth waiting to find out.
     */
    private suspend fun reach(waiting: Boolean): TheWindow {
        connected()?.let { return TheWindow.Answering(it) }
        if (!waiting) return TheWindow.StillOpening

        return connected(within = startingLimit)
            ?.let(TheWindow::Answering)
            ?: TheWindow.StillOpening
    }

    /** The conversation with the window, opening one if there is none — one attempt, no waiting. */
    private suspend fun connected(): Client? =
        window.get() ?: opening.withLock { window.get() ?: open() }

    /** The same, retried until [within] runs out. */
    private suspend fun connected(within: Duration): Client? {
        val deadline = TimeSource.Monotonic.markNow() + within
        while (true) {
            connected()?.let { return it }
            val remaining = -deadline.elapsedNow()
            if (remaining <= Duration.ZERO) return null
            delay(minOf(RETRY_INTERVAL, remaining))
        }
    }

    /**
     * Opens the conversation, or answers that there is none to be had.
     *
     * The address and the token are read at this moment rather than remembered, because a window
     * that has moved its port or minted a new token since this process started is still the window,
     * and the configuration a client holds says nothing about either.
     */
    private suspend fun open(): Client? {
        val choice = settings.currentChoice()
        // No token was ever minted, so the app has never had a server to present one to, and there
        // is nothing on the other end to authorise against.
        val token = choice.token ?: return null

        val client = WindowConversation()
        return try {
            client.connect(
                StreamableHttpClientTransport(
                    client = engine.value,
                    url = "http://$LOOPBACK_HOST:${choice.port}$MCP_PATH",
                ) {
                    headers.append(HttpHeaders.Authorization, "Bearer $token")
                },
            )
            client.also { window.set(it) }
        } catch (cause: Throwable) {
            currentCoroutineContext().ensureActive()
            runCatching { client.close() }
            null
        }
    }

    private suspend fun hangUp() {
        window.getAndSet(null)?.let { runCatching { it.close() } }
    }

    /**
     * The client this bridge holds, which forgets itself the moment the conversation ends.
     *
     * Without it a window that was closed and opened again would be answered on the transport of
     * the first one — dead, and indistinguishable from a live one until a call failed on it. The
     * announcement handler is installed here, on the one object that hears it, rather than after
     * connecting: a notification that arrived during the handshake would otherwise be dropped.
     */
    private inner class WindowConversation : Client(
        Implementation(name = CLIENT_NAME, version = McpSessionFactory.SERVER_VERSION),
    ) {

        init {
            setNotificationHandler<ToolListChangedNotification>(
                Method.Defined.NotificationsToolsListChanged,
            ) {
                announcing.async {
                    listener?.let { session -> runCatching { session.sendToolListChanged() } }
                    Unit
                }
            }
        }

        override fun onClose() {
            window.compareAndSet(this, null)
        }
    }

    /** What the bridge found where the window's server would be. */
    private sealed interface TheWindow {

        /** Answering, at the address and behind the token the app persisted. */
        data class Answering(val client: Client) : TheWindow

        /** Owning the database and not listening — a window on its way up. */
        data object StillOpening : TheWindow
    }

    companion object {

        /**
         * How long a call waits for a window that owns the database and is not listening yet.
         *
         * Five seconds. The window takes the ownership before it builds anything, so this is the
         * whole of its start-up as seen from outside; past it, telling the agent to make the call
         * again is a better answer than a call left hanging on a program that may never bind.
         */
        val WINDOW_IS_STARTING_LIMIT: Duration = 5.seconds

        /** How long a refused attempt waits before the next one. */
        private val RETRY_INTERVAL: Duration = 100.milliseconds

        /** What the window is told it is talking to, in its own list of connected clients. */
        private const val CLIENT_NAME = "finsight-stdio"

        private fun refusal(text: String) = McpToolResult(
            text = text,
            outcome = AgentActivity.Outcome.REFUSED,
        )

        /**
         * A window that owns the database and has not begun answering yet.
         *
         * Reachable so a test can name the answer it is asking about rather than matching a word
         * that several of them share.
         */
        val STILL_OPENING: String = agentJson.encodeToString(
            AgentRefusal(
                reason = "The Finsight app is opening on this machine and is not answering yet. " +
                    "While its window is open it is the app itself that answers an agent, so " +
                    "nothing was run here and nothing was changed. Tell the user the app is " +
                    "starting, and make the same call again in a moment.",
            ),
        )

        /** A window that was answering this very call and stopped. */
        private val WINDOW_WENT_AWAY: String = agentJson.encodeToString(
            AgentRefusal(
                reason = "The Finsight app was answering this call and its window was closed " +
                    "before it did, so whether it took effect is not known. Check before asking " +
                    "for it again. The session is unaffected: the app is now closed, and the " +
                    "next call is carried out here.",
            ),
        )
    }
}
