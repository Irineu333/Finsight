package com.neoutils.finsight.mcp

import com.neoutils.finsight.database.entity.AccountEntity
import com.neoutils.finsight.database.entity.TransactionEntity
import com.neoutils.finsight.database.repository.LedgerEntryWriter
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.ContraLeg
import com.neoutils.finsight.domain.model.TransactionLeg
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.feature.mcp.api.AgentActivity
import com.neoutils.finsight.feature.mcp.api.McpStdout
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The headless surface, over a real protocol on real pipes.
 *
 * Everything here is the session the desktop resolves, speaking to the SDK's own client through
 * operating-system pipes — the same exchange a launched `--mcp` process holds with the agent that
 * launched it, minus only the launching. The window is closed throughout, which is what the free
 * ownership means: no other process holds the archive, so this one executes.
 *
 * The suite next door asserts the same surface over a socket. That the two answer alike is not a
 * coincidence to be maintained: both are assembled by `McpSessionFactory`, and what differs here is
 * only who is allowed to run a call and what the app's switch makes of it.
 */
class McpStdioOverTheProtocolTest {

    /** The handshake and the list, with nothing else in the way — what a client does first. */
    @Test
    fun `a client initialises and reads the tools with no window open`() = runBlocking {
        val tool = SpyTool(name = McpToolName.LIST_ACCOUNTS.wireName, effect = McpToolEffect.READS)

        McpServerHarness(tools = listOf(tool)).use { harness ->
            harness.serverSettings.setEnabled(true)

            harness.stdioSession().servedOverStdio { client ->
                assertEquals(
                    "finsight",
                    client.serverVersion?.name,
                    "The client reached something other than this app.",
                )
                assertNotNull(
                    client.serverCapabilities?.tools,
                    "The handshake did not declare tools, so a client has no reason to list them.",
                )
                assertEquals(
                    listOf(tool.name),
                    client.listTools().tools.map { it.name },
                    "The stdio session did not announce the tool the app offers.",
                )
            }
        }
    }

    /**
     * A question and an act, with the app closed — the whole reason this mode exists.
     *
     * The write is a real double-entry one, through the ledger's own boundary, because a write that
     * did not go through it is not the kind of write the app would have to answer for. And the act
     * leaves a row in the activity log, which is where authorship of an agent's write is kept: the
     * user opens the app tomorrow and reads what was done while it was shut.
     */
    @Test
    fun `a consultation and a write with the app closed, and the write is on the record`() =
        runBlocking {
            McpServerHarness().use { harness ->
                val accountDao = harness.database.accountDao()
                val transactionDao = harness.database.transactionDao()
                val boundary = LedgerEntryWriter(
                    entryDao = harness.database.entryDao(),
                    accountDao = accountDao,
                    dimensionDao = harness.database.dimensionDao(),
                )
                val accountId = accountDao.insert(AccountEntity(name = "Checking", currency = "BRL"))

                val question = SpyTool(
                    name = McpToolName.LIST_ACCOUNTS.wireName,
                    effect = McpToolEffect.READS,
                    answer = {
                        McpToolResult(text = accountDao.getAllLedgerAccounts().joinToString { it.name })
                    },
                )
                val act = SpyTool(
                    name = McpToolName.CREATE_TRANSACTION.wireName,
                    effect = McpToolEffect.CHANGES,
                    answer = {
                        val transactionId = transactionDao.insert(
                            TransactionEntity(title = "Coffee", date = LocalDate(2026, 3, 1)),
                        )
                        boundary.writeEntries(
                            transactionId = transactionId,
                            legs = listOf(
                                TransactionLeg(
                                    type = TransactionType.EXPENSE,
                                    amount = 12.0,
                                    accountId = accountId,
                                ),
                            ),
                            contra = ContraLeg(nature = AccountType.EXPENSE),
                        )
                        McpToolResult(
                            text = "Recorded.",
                            summary = "Coffee, 12,00, on Checking",
                            reference = AgentActivity.Reference(
                                kind = AgentActivity.Reference.Kind.TRANSACTION,
                                id = transactionId,
                            ),
                        )
                    },
                )

                harness.tools += listOf(question, act)
                harness.serverSettings.setEnabled(true)

                harness.stdioSession().servedOverStdio { client ->
                    assertEquals(
                        "Checking",
                        client.callTool(question.name, emptyMap()).text(),
                        "The consultation did not come from the archive.",
                    )

                    val written = client.callTool(act.name, emptyMap())
                    assertEquals("Recorded.", written.text(), "The write did not go through.")
                    assertFalse(written.isError ?: false, "An applied write arrived as a refusal.")
                }

                assertEquals(
                    -1200L,
                    harness.database.entryDao().balanceOf(accountId),
                    "The archive does not hold what the agent wrote into it.",
                )

                val recorded = harness.activity.observeAll().first()
                assertEquals(
                    listOf(McpToolName.CREATE_TRANSACTION.wireName),
                    recorded.map { it.operation },
                    "The log kept something other than the one act: a read is not an act.",
                )
                assertEquals(
                    AgentActivity.Outcome.APPLIED,
                    recorded.single().outcome,
                    "The act that went through was recorded as something else.",
                )
            }
        }

    /**
     * The switch is the app's, and it governs this mode too (design D7).
     *
     * The process speaks — it has to, because a client that launched a program and watched it die
     * reports a broken app rather than a switch its user turned off — and then offers nothing and
     * refuses everything, naming the section where the user reverses it. An installation where
     * nobody ever enabled the server is this same case: the harness's preferences are empty, and
     * nothing here switches anything on.
     */
    @Test
    fun `with the server switched off nothing is offered and every call is refused`() = runBlocking {
        val tool = SpyTool(name = McpToolName.LIST_ACCOUNTS.wireName, effect = McpToolEffect.READS)

        McpServerHarness(tools = listOf(tool)).use { harness ->
            assertFalse(
                harness.serverSettings.isEnabled.value,
                "the case being asked about is an installation where nobody ever switched it on",
            )

            harness.stdioSession().servedOverStdio { client ->
                assertTrue(
                    client.listTools().tools.isEmpty(),
                    "A switched-off server announced tools.",
                )

                val refused = client.callTool(tool.name, emptyMap())
                assertTrue(refused.isError ?: false, "The refusal did not arrive as one.")
                assertContains(
                    refused.text(),
                    McpPermissionNotice.THE_SECTION,
                    message = "The refusal did not say where the user switches the server back on.",
                )
                assertEquals(0, tool.calls, "The tool ran on a server that is switched off.")

                // Every call, and not only the ones on a name the app has. A switched-off app is
                // not offering a surface at all, so it says nothing about which names are on it —
                // and it must never answer *"tool not found"* on a name that would work, which is
                // the false statement about the app that the whole notice exists to prevent.
                val invented = client.callTool("get_horoscope", emptyMap())
                assertTrue(invented.isError ?: false, "An invented name was not refused.")
                assertContains(
                    invented.text(),
                    McpPermissionNotice.THE_SECTION,
                    message = "A switched-off server answered a call by telling the agent about " +
                        "its own list of names instead of about the switch.",
                )

                assertContains(
                    assertNotNull(client.serverInstructions, "the handshake said nothing"),
                    McpPermissionNotice.THE_SECTION,
                    message = "The handshake did not say why nothing is on offer.",
                )
            }

            assertTrue(
                harness.activity.observeAll().first().isEmpty(),
                "A server that is switched off wrote to the archive to say it did nothing.",
            )
        }
    }

    /**
     * The switch is moved **while a session is running**, and the session obeys it.
     *
     * The window is closed throughout, so this process is the one that would carry the call out —
     * which is what makes it the case that matters: the requirement is that a switched-off server
     * refuses *every* call, not every call that arrived after a process happened to start. A
     * session that read the switch once, when it began, would go on executing here for as long as
     * the agent stayed connected, with the app saying it was offering nothing.
     */
    @Test
    fun `the server is switched off during a session and the next call is refused`() = runBlocking {
        val tool = SpyTool(name = McpToolName.CREATE_TRANSACTION.wireName, effect = McpToolEffect.CHANGES)

        McpServerHarness(tools = listOf(tool)).use { harness ->
            harness.serverSettings.setEnabled(true)

            harness.stdioSession().servedOverStdio { client ->
                assertEquals(
                    "done",
                    client.callTool(tool.name, emptyMap()).text(),
                    "The call was not carried out while the server was on.",
                )

                harness.serverSettings.setEnabled(false)

                val refused = client.callTool(tool.name, emptyMap())
                assertTrue(refused.isError ?: false, "The refusal did not arrive as one.")
                assertContains(
                    refused.text(),
                    McpPermissionNotice.THE_SECTION,
                    message = "The refusal did not say where the user switches the server on.",
                )
                assertEquals(1, tool.calls, "The tool ran after the user switched the server off.")

                assertTrue(
                    client.listTools().tools.isEmpty(),
                    "A server the user has just switched off went on announcing tools.",
                )
            }
        }
    }

    /**
     * And the other way round, which is the same defect seen from its other side.
     *
     * A process that started with the switch off has to become useful the moment the user switches
     * it on, without them having to know that their agent's client holds a process that read the
     * answer once. Nothing here reconnects: it is the same session, the same pipes.
     */
    @Test
    fun `the server is switched on during a session that began with it off`() = runBlocking {
        val tool = SpyTool(name = McpToolName.LIST_ACCOUNTS.wireName, effect = McpToolEffect.READS)

        McpServerHarness(tools = listOf(tool)).use { harness ->
            assertFalse(
                harness.serverSettings.isEnabled.value,
                "the case being asked about is a session that began with the server off",
            )

            harness.stdioSession().servedOverStdio { client ->
                assertTrue(
                    client.listTools().tools.isEmpty(),
                    "A switched-off server announced tools.",
                )

                harness.serverSettings.setEnabled(true)

                assertEquals(
                    listOf(tool.name),
                    client.listTools().tools.map { it.name },
                    "The user switched the server on and the session went on offering nothing.",
                )
                assertEquals(
                    "done",
                    client.callTool(tool.name, emptyMap()).text(),
                    "The user switched the server on and the call was still refused.",
                )
            }
        }
    }

    /**
     * The opening line, on the channel clients display (design D6).
     *
     * Three facts, and each answers a question a session that went wrong raises: which build
     * answered — a client configuration can name a path that is no longer the one installed —, that
     * it is this mode and not the window's, and whether the app is offering anything at all, which
     * is what a `tools/list` of nothing means.
     */
    @Test
    fun `the process says on stderr which build it is, in what mode, and what the switch says`() =
        runBlocking {
            val said = ByteArrayOutputStream()

            McpServerHarness().use { harness ->
                harness.serverSettings.setEnabled(true)

                harness.stdioSession(diagnostics = PrintStream(said, true))
                    .servedOverStdio { client -> client.listTools() }
            }

            val line = said.toString().trim()
            assertContains(line, "finsight", message = "the line does not say which app answered")
            assertContains(
                line,
                "development build",
                message = "the line does not say which build answered",
            )
            assertContains(line, "mcp stdio mode", message = "the line does not name the mode")
            assertContains(line, "server enabled", message = "the line does not say what the switch says")
        }

    /**
     * The process's own streams, and the hygiene of `stdout` on them (design D6).
     *
     * This is the only test that serves the way the entry point will: no streams stated, the
     * session reading `System.in` and writing to what the claim kept. The staging is what an
     * operating system hands a launched process — `System.out` **is** the wire — and
     * `McpStdout.claim` is what takes it for the protocol and puts everything else on the
     * diagnostics stream.
     *
     * A `println` in the middle of the conversation is all it takes to break a session, and a
     * library does one without asking: the text would land inside a frame, and the client's parser
     * never recovers from that. Here it lands on `stderr` instead, and the conversation carries on.
     *
     * It claims for the whole test JVM, which is why there is one of these and not several: the
     * claim is a process-wide fact, and a second test making its own would be handed the first
     * one's.
     */
    @Test
    fun `the process serves its own streams, and a println does not corrupt the protocol`() =
        runBlocking {
            val tool = SpyTool(name = McpToolName.LIST_ACCOUNTS.wireName, effect = McpToolEffect.READS)
            val pipes = StdioPipes()
            val standardOutput = System.out
            val standardInput = System.`in`

            try {
                System.setIn(pipes.serverIn)
                System.setOut(PrintStream(pipes.serverOut, true))
                McpStdout.claim()
                assertSame(
                    System.err,
                    System.out,
                    "the claim left other output on the stream the protocol is written to",
                )

                McpServerHarness(tools = listOf(tool)).use { harness ->
                    harness.serverSettings.setEnabled(true)

                    harness.stdioSession().servedOverStdio(
                        pipes = pipes,
                        onTheProcessOwnStreams = true,
                    ) { client ->
                        assertEquals(listOf(tool.name), client.listTools().tools.map { it.name })

                        println("a library announcing itself")
                        System.out.println("""{"jsonrpc":"2.0","id":99,"result":{}}""")
                        System.out.flush()

                        assertEquals(
                            "done",
                            client.callTool(tool.name, emptyMap()).text(),
                            "The conversation did not survive something written to System.out.",
                        )
                        assertEquals(
                            listOf(tool.name),
                            client.listTools().tools.map { it.name },
                            "The session did not survive something written to System.out.",
                        )
                    }
                }
            } finally {
                System.setOut(standardOutput)
                System.setIn(standardInput)
            }
        }

    /**
     * Two clients, two sessions, both writing at once.
     *
     * Inside one process the two share the single claim on the archive — a JDK file lock belongs to
     * the whole JVM, and holders within it stand on the same one — so this asks what remains once
     * the exclusion is out of the way: that two conversations running at the same time each apply
     * their write and neither fails. That the exclusion itself holds *between* processes is
     * `DatabaseOwnershipTest`'s subject, in the module that owns the lock.
     */
    @Test
    fun `two sessions writing at the same time both apply their write`() = runBlocking {
        McpServerHarness().use { harness ->
            val transactionDao = harness.database.transactionDao()
            val tool = SpyTool(
                name = McpToolName.CREATE_TRANSACTION.wireName,
                effect = McpToolEffect.CHANGES,
                answer = { arguments ->
                    val title = arguments?.get("title")?.jsonPrimitive?.content.orEmpty()
                    transactionDao.insert(TransactionEntity(title = title, date = LocalDate(2026, 3, 1)))
                    McpToolResult(text = "Recorded.", summary = title)
                },
            )

            harness.tools += tool
            harness.serverSettings.setEnabled(true)

            coroutineScope {
                listOf("first", "second").map { title ->
                    async {
                        harness.stdioSession().servedOverStdio { client ->
                            val answer = client.callTool(tool.name, mapOf("title" to title))
                            assertEquals("Recorded.", answer.text(), "the $title write did not apply")
                        }
                    }
                }.awaitAll()
            }

            assertEquals(
                2,
                harness.database.transactionDao().getAll().size,
                "Two sessions wrote and the archive holds something other than two postings.",
            )
            assertEquals(
                2,
                harness.activity.observeAll().first().size,
                "Two acts happened and the log kept a different number of them.",
            )
        }
    }
}

/** The one piece of text a call comes back with. */
private fun CallToolResult.text(): String = (content.single() as TextContent).text
