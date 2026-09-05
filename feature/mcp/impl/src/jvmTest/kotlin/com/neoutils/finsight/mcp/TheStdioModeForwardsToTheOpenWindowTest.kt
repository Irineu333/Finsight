package com.neoutils.finsight.mcp

import com.neoutils.finsight.feature.mcp.api.McpPermissionAxis
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Method
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolListChangedNotification
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * **With the window open, the window answers — and the client never notices the change of hands.**
 *
 * The staging is the real one, and it has to be: the ownership of the archive is held by a process
 * of its own, because a JDK file lock belongs to the whole JVM and a claim taken in the test would
 * be handed back to the session under test. The socket is this app's own controller, over loopback,
 * behind its token; the headless session is the one the desktop resolves, over operating-system
 * pipes; and the client is the SDK's own, which knows nothing of either.
 *
 * **How "who answered" is read.** The two processes offer tools of the **same names** — they are the
 * same build, so they must — and in these tests the bodies differ: the one registered in the window
 * says `the window`, the one registered in the headless process says `here`. The answer that comes
 * back names the process that ran it, which is the only fact these scenarios are about. In
 * production the two bodies are the same code; here they are a probe.
 */
class TheStdioModeForwardsToTheOpenWindowTest {

    /**
     * The window opens in the middle of a conversation (`mcp-stdio-mode`).
     *
     * The first call is this process's own, because nothing else owns the archive. Then the user
     * opens the app, and without a word to the client — the same session, the same pipes, no
     * reconnection — the next call is carried out by the window.
     */
    @Test
    fun `the window opens in the middle of the session and the next call goes to it`() =
        runBlocking {
            val here = answering("here")
            val theWindow = answering("the window")

            McpServerHarness(tools = listOf(theWindow)).use { harness ->
                harness.serverSettings.setEnabled(true)

                harness.stdioSession(tools = listOf(here)).servedOverStdio { client ->
                    assertEquals(
                        "here",
                        client.callTool(here.name, emptyMap()).text(),
                        "With no window open the call was not carried out by this process.",
                    )

                    val window = harness.openTheWindow()
                    try {
                        assertEquals(
                            "the window",
                            client.callTool(here.name, emptyMap()).text(),
                            "The window was open and the call was still carried out here.",
                        )
                    } finally {
                        harness.closeTheWindow(window)
                    }
                }

                assertEquals(1, here.calls, "This process ran a call it should not have.")
                assertEquals(1, theWindow.calls, "The window ran a call it should not have.")
            }
        }

    /**
     * And the window closes in the middle of one (`mcp-stdio-mode`).
     *
     * The same session again: what changes is only who carries the call out, and the client is
     * never disconnected, never re-handshaken and never told.
     */
    @Test
    fun `the window closes in the middle of the session and the next call is carried out here`() =
        runBlocking {
            val here = answering("here")
            val theWindow = answering("the window")

            McpServerHarness(tools = listOf(theWindow)).use { harness ->
                harness.serverSettings.setEnabled(true)
                val window = harness.openTheWindow()

                harness.stdioSession(tools = listOf(here)).servedOverStdio { client ->
                    assertEquals(
                        "the window",
                        client.callTool(here.name, emptyMap()).text(),
                        "The window was open and did not answer.",
                    )

                    harness.closeTheWindow(window)

                    assertEquals(
                        "here",
                        client.callTool(here.name, emptyMap()).text(),
                        "The app was closed and the call was not carried out by this process.",
                    )
                }

                assertEquals(1, here.calls, "This process ran a number of calls it was not asked.")
                assertEquals(1, theWindow.calls, "The window ran a number of calls it was not asked.")
            }
        }

    /**
     * `tools/list` is forwarded too, and not only `tools/call` (`mcp-stdio-mode`).
     *
     * The two processes announce the same **name**, because they are one build; what differs here is
     * the description, which is the only thing in an announcement that can say which of them wrote
     * it. Read with the window open it is the window's, and after it closes it is this process's —
     * the same question asked of the same session, twice.
     *
     * It matters beyond tidiness: what a tool list may contain is the user's live choice, and this
     * process's copy of it was read from disk when it started. A session that forwarded its calls
     * and listed from here would be one server saying two things (design D7).
     */
    @Test
    fun `the list with the window open is the window's own`() = runBlocking {
        val here = answering("here")
        val theWindow = answering("the window")

        McpServerHarness(tools = listOf(theWindow)).use { harness ->
            harness.serverSettings.setEnabled(true)
            val window = harness.openTheWindow()

            harness.stdioSession(tools = listOf(here)).servedOverStdio { client ->
                assertEquals(
                    listOf("offered by the window"),
                    client.listTools().tools.map { it.description },
                    "The list with the window open did not come from the window.",
                )

                harness.closeTheWindow(window)

                assertEquals(
                    listOf("offered by here"),
                    client.listTools().tools.map { it.description },
                    "The app closed and the list still came from somewhere else.",
                )
            }
        }
    }

    /**
     * The same list in both modes (`mcp-stdio-mode`).
     *
     * Here the two processes offer what they really do offer — the same declaration, because they
     * are the same build — and the whole announcement is compared, name, description and schema
     * alike. Forwarding that dropped or reshaped any of it would show up as a client whose tools
     * changed under it because the user opened their app, and nothing about their permissions did.
     *
     * Asked of one session across the window closing, so what is compared is two answers given to
     * one client rather than two runs of a test.
     */
    @Test
    fun `the list is the same whether the window is open or closed`() = runBlocking {
        val offered = answering("the app")

        McpServerHarness(tools = listOf(offered)).use { harness ->
            harness.serverSettings.setEnabled(true)
            val window = harness.openTheWindow()

            harness.stdioSession().servedOverStdio { client ->
                val forwarded = client.listTools().tools
                assertEquals(
                    listOf(offered.name),
                    forwarded.map { it.name },
                    "The window announced something other than what the app offers.",
                )

                harness.closeTheWindow(window)

                assertEquals(
                    forwarded,
                    client.listTools().tools,
                    "The app closing changed what the client is offered.",
                )
            }
        }
    }

    /**
     * A permission moved in the open app reaches a client connected by the stdio mode
     * (`mcp-permissions`).
     *
     * The announcement is the window's own, sent over its socket to the bridge, and repeated by the
     * bridge down the pipe to the client — which is the whole point of holding the conversation with
     * the window open rather than dialling per call.
     *
     * The switch is moved **until** one is heard, and not once: the transport opens its standalone
     * event stream on a coroutine of its own after the handshake, nothing says when it is up, and an
     * announcement sent before it is gone — the protocol has no replay. Hearing one is the only
     * signal there is, so the loop is the synchronisation and not a retry over a flaky server.
     */
    @Test
    fun `a permission moved in the window reaches the client through the bridge`() = runBlocking {
        val theWindow = answering("the window")

        McpServerHarness(tools = listOf(theWindow)).use { harness ->
            harness.serverSettings.setEnabled(true)
            val window = harness.openTheWindow()

            try {
                harness.stdioSession(tools = listOf(answering("here"))).servedOverStdio { client ->
                    val announced = CompletableDeferred<Unit>()
                    client.setNotificationHandler<ToolListChangedNotification>(
                        Method.Defined.NotificationsToolsListChanged,
                    ) {
                        announced.complete(Unit)
                        CompletableDeferred(Unit)
                    }

                    // The bridge holds no conversation with the window until it has something to
                    // forward, and it is that conversation the announcement travels on.
                    assertEquals(listOf(theWindow.name), client.listTools().tools.map { it.name })

                    withTimeout(ANNOUNCEMENT_TIMEOUT_MILLIS) {
                        while (!announced.isCompleted) {
                            harness.controller.setPermission(McpPermissionAxis.READ, granted = false)
                            delay(SETTLE_MILLIS)
                            if (announced.isCompleted) break
                            harness.controller.setPermission(McpPermissionAxis.READ, granted = true)
                            delay(SETTLE_MILLIS)
                        }
                        announced.await()
                    }

                    // Whatever half of the toggling the loop left behind, the axis ends withheld —
                    // and moving it to where it already is announces nothing.
                    harness.controller.setPermission(McpPermissionAxis.READ, granted = false)

                    assertTrue(
                        client.listTools().tools.isEmpty(),
                        "The client was told the list changed and read the old one back.",
                    )
                }
            } finally {
                harness.closeTheWindow(window)
            }
        }
    }

    /**
     * The window is open with the server switched off (`mcp-stdio-mode`).
     *
     * The app owns the archive and is offering nothing, so there is no port to reach — the same
     * silence a window that is still starting leaves. What separates them is the switch as the store
     * holds it *now*, and it is read before anything is waited for: a user who switched the server
     * off gets an answer, not five seconds of nothing (design D10).
     *
     * The switch is moved after the session is serving, which is the only way this case exists at
     * all: a process that read the switch off when it started speaks a server of its own and never
     * reaches the bridge.
     */
    @Test
    fun `with the server switched off in the app the call is refused at once`() = runBlocking {
        val here = answering("here")

        McpServerHarness().use { harness ->
            harness.serverSettings.setEnabled(true)

            ArchiveHolder(harness.databasePath).use { window ->
                assertEquals(HELD, window.next(), "the window did not take the ownership")

                harness.stdioSession(tools = listOf(here)).servedOverStdio { client ->
                    harness.serverSettings.setEnabled(false)

                    val started = TimeSource.Monotonic.markNow()
                    val refused = client.callTool(here.name, emptyMap())
                    val took = started.elapsedNow()

                    assertTrue(refused.isError ?: false, "The refusal did not arrive as one.")
                    assertContains(
                        refused.text(),
                        McpPermissionNotice.THE_SECTION,
                        message = "The refusal did not say where the user switches the server on.",
                    )
                    assertTrue(
                        took < McpBridge.WINDOW_IS_STARTING_LIMIT,
                        "The refusal took $took: a switched-off server was waited for as though " +
                            "it were a window still opening.",
                    )
                    assertEquals(0, here.calls, "The tool ran on a server that is switched off.")
                }
            }

            assertTrue(
                harness.activity.observeAll().first().isEmpty(),
                "A process that may not touch the archive wrote a row into it.",
            )
        }
    }

    /**
     * The window has taken the archive and is not answering yet (`mcp-stdio-mode`).
     *
     * The switch is on, so the silence is a window on its way up: the call waits for it, and when
     * the limit runs out the client is told the app is starting and to ask again — never that the
     * call was carried out, and never left hanging on a program that may never bind.
     *
     * The limit is shortened here so the run does not spend the real one waiting; that the real one
     * is five seconds is asserted on the constant itself, which is where the number is declared.
     */
    @Test
    fun `a call while the window is still starting is answered with make it again`() = runBlocking {
        val here = answering("here")

        assertEquals(
            5.seconds,
            McpBridge.WINDOW_IS_STARTING_LIMIT,
            "The declared limit moved, and D10 states it.",
        )

        McpServerHarness().use { harness ->
            harness.serverSettings.setEnabled(true)

            ArchiveHolder(harness.databasePath).use { window ->
                assertEquals(HELD, window.next(), "the window did not take the ownership")

                val impatient = McpBridge(harness.serverSettings, startingLimit = 300.milliseconds)

                harness.stdioSession(tools = listOf(here), bridge = impatient)
                    .servedOverStdio { client ->
                        val refused = client.callTool(here.name, emptyMap())

                        assertTrue(refused.isError ?: false, "The refusal did not arrive as one.")
                        assertContains(
                            refused.text(),
                            "again",
                            message = "The answer did not tell the agent to make the call again.",
                        )
                        assertEquals(0, here.calls, "The tool ran without the ownership.")
                    }
            }

            assertTrue(
                harness.activity.observeAll().first().isEmpty(),
                "A process that may not touch the archive wrote a row into it.",
            )
        }
    }

    /**
     * A tool that says which process holds it, under a name the surface knows.
     *
     * The name is the same on both sides because a client calls one name and the two processes are
     * one build. What differs is the answer, which is how these tests ask who *ran* it, and the
     * description, which is how they ask who *announced* it.
     */
    private fun answering(who: String) = SpyTool(
        name = McpToolName.LIST_ACCOUNTS.wireName,
        effect = McpToolEffect.READS,
        description = "offered by $who",
        answer = { McpToolResult(text = who) },
    )

    private companion object {

        /**
         * The deadline on the toggling loop as a whole: an announcement that never arrives fails
         * the run instead of hanging it.
         */
        const val ANNOUNCEMENT_TIMEOUT_MILLIS = 30_000L

        /** How long one turn of the loop leaves the announcement to travel two hops. */
        const val SETTLE_MILLIS = 150L
    }
}

/** The one piece of text a call comes back with. */
private fun CallToolResult.text(): String = (content.single() as TextContent).text
