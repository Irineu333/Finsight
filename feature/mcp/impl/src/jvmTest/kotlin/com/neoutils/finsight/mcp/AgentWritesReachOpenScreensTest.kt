package com.neoutils.finsight.mcp

import com.neoutils.finsight.database.entity.AccountEntity
import com.neoutils.finsight.database.entity.TransactionEntity
import com.neoutils.finsight.database.repository.EntryRepository
import com.neoutils.finsight.database.repository.LedgerEntryWriter
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.ContraLeg
import com.neoutils.finsight.domain.model.TransactionLeg
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.feature.mcp.api.AgentActivity
import com.neoutils.finsight.mcp.McpServerHarness.Companion.freePort
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The whole reason the server lives inside the app rather than beside it (design D1).
 *
 * Room's invalidation tracker is **of the process**. A second program writing to the same file
 * would wake no `Flow` in the running app, and the user's screen would go on showing figures from
 * before the agent's write. What this asks is exactly that: a screen is already collecting, an
 * agent writes over the socket, and the screen's own `Flow` produces the new figure without anyone
 * reloading, reopening or navigating.
 *
 * The write is a real double-entry one, through the ledger's own boundary, because a write that did
 * not go through it is not the kind of write the app would have to react to.
 *
 * **It is asked twice, of the two ways an agent reaches the app.** Over the socket, which is a
 * client the user configured against the open window. And over the standard streams, where a client
 * launched a process of its own and the window is open — there the process may not touch the
 * archive at all, and the requirement holds only because the call is forwarded to the window and
 * carried out *there* (design D3). A stdio session that wrote locally would leave the screen showing
 * yesterday's figure with nothing failing, which is exactly the defect this file exists for.
 */
class AgentWritesReachOpenScreensTest {

    @Test
    fun `a write over the socket reaches a flow that was already being collected`() = runTest {
        val port = freePort()

        McpServerHarness().use { harness ->
            val accountDao = harness.database.accountDao()
            val entryDao = harness.database.entryDao()
            val transactionDao = harness.database.transactionDao()
            val boundary = LedgerEntryWriter(
                entryDao = entryDao,
                accountDao = accountDao,
                dimensionDao = harness.database.dimensionDao(),
            )
            val entries = EntryRepository(entryDao)

            val accountId = accountDao.insert(AccountEntity(name = "Checking", currency = "BRL"))

            val tool = SpyTool(
                name = "create_transaction",
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

            harness.tools += tool
            harness.controller.setPort(port)
            harness.controller.setEnabled(true)
            val token = assertNotNull(harness.controller.token.value)

            withContext(Dispatchers.IO) {
                // The open screen: it subscribes once and never asks again.
                val seen = MutableStateFlow(emptyList<Long>())
                val screen = launch {
                    entries.observeLedgerChanges().collect {
                        seen.update { balances -> balances + entryDao.balanceOf(accountId) }
                    }
                }

                withTimeout(TIMEOUT_MILLIS) { seen.first { it.size == 1 } }
                assertEquals(
                    listOf(0L),
                    seen.value,
                    "The screen did not start from the balance as it was before the write.",
                )

                McpConversation(port, token).open().callTool(tool.name)

                val balances = withTimeout(TIMEOUT_MILLIS) { seen.first { it.size >= 2 } }

                assertEquals(
                    -1200L,
                    balances.last(),
                    "The screen was woken and read a balance that does not include the write.",
                )

                screen.cancel()
            }

            assertEquals(1, tool.calls, "The agent's call did not reach the tool.")
            assertTrue(
                entryDao.getAll().size == 2,
                "The write did not land as a balanced pair of entries.",
            )
            assertEquals(
                1,
                harness.activity.observeAll().first().size,
                "The write reached the screen and left no record of who made it.",
            )

            harness.controller.stop()
        }
    }

    /**
     * The same claim, through the mode a client launches (`mcp-stdio-mode`).
     *
     * The window is open — another process owns the archive, and this app's own server is
     * listening — so the headless session may not write. It forwards, the window carries the write
     * out, and the screen that was already collecting is woken by the tracker of the process that
     * did the writing, which is the only process whose tracker could ever wake it.
     *
     * The tool registered in the headless process bears the same name and writes nothing: if it
     * ever ran, the row would be there and the screen would still be showing the old figure — the
     * failure the forwarding exists to prevent, made visible.
     */
    @Test
    fun `a write forwarded from a stdio session reaches a flow that was already being collected`() =
        runBlocking {
            McpServerHarness().use { harness ->
                val accountDao = harness.database.accountDao()
                val entryDao = harness.database.entryDao()
                val transactionDao = harness.database.transactionDao()
                val boundary = LedgerEntryWriter(
                    entryDao = entryDao,
                    accountDao = accountDao,
                    dimensionDao = harness.database.dimensionDao(),
                )
                val entries = EntryRepository(entryDao)

                val accountId = accountDao.insert(AccountEntity(name = "Checking", currency = "BRL"))

                val inTheWindow = SpyTool(
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

                /** What the headless process offers under the same name, and must never run. */
                val inTheHeadlessProcess = SpyTool(
                    name = McpToolName.CREATE_TRANSACTION.wireName,
                    effect = McpToolEffect.CHANGES,
                    answer = { McpToolResult(text = "Recorded.", summary = "nothing at all") },
                )

                harness.tools += inTheWindow
                harness.serverSettings.setEnabled(true)
                val window = harness.openTheWindow()

                try {
                    // The open screen: it subscribes once and never asks again.
                    val seen = MutableStateFlow(emptyList<Long>())
                    val screen = launch(Dispatchers.IO) {
                        entries.observeLedgerChanges().collect {
                            seen.update { balances -> balances + entryDao.balanceOf(accountId) }
                        }
                    }

                    withTimeout(TIMEOUT_MILLIS) { seen.first { it.size == 1 } }
                    assertEquals(
                        listOf(0L),
                        seen.value,
                        "The screen did not start from the balance as it was before the write.",
                    )

                    harness.stdioSession(tools = listOf(inTheHeadlessProcess))
                        .servedOverStdio { client ->
                            val written = client.callTool(inTheWindow.name, emptyMap())
                            assertEquals(
                                "Recorded.",
                                (written.content.single() as TextContent).text,
                                "The forwarded write did not go through.",
                            )
                        }

                    val balances = withTimeout(TIMEOUT_MILLIS) { seen.first { it.size >= 2 } }

                    assertEquals(
                        -1200L,
                        balances.last(),
                        "The screen was woken and read a balance that does not include the write.",
                    )

                    screen.cancel()
                } finally {
                    harness.closeTheWindow(window)
                }

                assertEquals(1, inTheWindow.calls, "The window did not carry the write out.")
                assertEquals(
                    0,
                    inTheHeadlessProcess.calls,
                    "The headless process wrote to an archive that was another process's.",
                )
                assertEquals(
                    1,
                    harness.activity.observeAll().first().size,
                    "The write reached the screen and left no record of who made it.",
                )
            }
        }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
    }
}
