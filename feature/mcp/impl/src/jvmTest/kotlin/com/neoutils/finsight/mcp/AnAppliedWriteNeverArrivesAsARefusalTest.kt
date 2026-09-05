package com.neoutils.finsight.mcp

import arrow.core.Either
import arrow.core.right
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionRegistration
import com.neoutils.finsight.domain.model.form.TransactionForm
import com.neoutils.finsight.domain.usecase.AddInstallmentUseCase
import com.neoutils.finsight.domain.usecase.RegisterTransactionUseCase
import com.neoutils.finsight.domain.usecase.UpdateTransactionUseCase
import com.neoutils.finsight.feature.mcp.api.AgentActivity
import com.neoutils.finsight.mcp.McpServerHarness.Companion.freePort
import com.neoutils.finsight.mcp.tool.CreateInstallmentTool
import com.neoutils.finsight.mcp.tool.CreateTransactionTool
import com.neoutils.finsight.mcp.tool.UpdateTransactionTool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * **A write that reached the ledger is never answered as a refusal.**
 *
 * The three tools that write a posting answer with the posting, and the mapper that produces it
 * keeps `null` for a transaction with no leg to be read through — the contract a listing relies on
 * to drop an item instead of failing on it. After a write there is nothing to drop: the row is in
 * the ledger by the time the mapping runs, so an answer that asserted the `null` away would raise on
 * the far side of the write, where `AgentActivityJournal`'s catch-all turns any throw into
 * `REFUSED` and *"The operation could not be completed."*
 *
 * That is the one failure of this surface an agent is rewarded for making worse. A deterministic
 * refusal is what a retry is *for*, and the retry writes the posting a second time — so the shape of
 * the answer matters here even where the `null` is out of reach: what is asserted below is that the
 * write path has no branch that can report an applied write as a refused one.
 *
 * The `null` is reached by rewiring the use case each tool calls, and nothing else: the tools are
 * the production ones, registered in the production registry, and reached over the wire, because the
 * outcome flag and the payload only exist once they have been serialised.
 */
class AnAppliedWriteNeverArrivesAsARefusalTest {

    @Test
    fun `create_transaction answers a posting it cannot read back as recorded, not as refused`() =
        runTest {
            withUnreadablePostings { _, harness, client ->
                val response = client.callTool(
                    "create_transaction",
                    """{"type":"expense","amount":45.90,"title":"Mercado","account_id":1}""",
                )

                response.assertReportsWhatItWrote()
                harness.assertLoggedAsApplied("create_transaction")
            }
        }

    @Test
    fun `update_transaction answers a posting it cannot read back as edited, not as refused`() =
        runTest {
            withUnreadablePostings { seeded, harness, client ->
                val response = client.callTool(
                    "update_transaction",
                    """{"id":${seeded.groceriesId},"amount":50.00}""",
                )

                response.assertReportsWhatItWrote()
                harness.assertLoggedAsApplied("update_transaction")
            }
        }

    @Test
    fun `create_installment answers postings it cannot read back as recorded, not as refused`() =
        runTest {
            withUnreadablePostings { seeded, harness, client ->
                val response = client.callTool(
                    "create_installment",
                    """{"card_id":${seeded.cards.single().id},"amount":300.00,"count":3,"title":"Fone"}""",
                )

                response.assertReportsWhatItWrote()
                harness.assertLoggedAsApplied("create_installment")
            }
        }

    // ----------------------------------------------------------------------------------
    // What "reported as what it was" means
    // ----------------------------------------------------------------------------------

    /**
     * The write went through, so the answer says so — and says it in words, because an agent that
     * has to infer the outcome from a missing field infers whatever it likes.
     */
    private fun RawHttp.Response.assertReportsWhatItWrote() {
        assertTrue(
            !isToolError(),
            "the write went through and came back flagged as an error the agent must act on: " +
                toolText(),
        )

        assertNotEquals(
            GENERIC_FAILURE,
            toolText(),
            "the write fell through to the journal's catch-all, which reports a completed write " +
                "as one that did not happen",
        )

        val note = payload().text("note")

        assertTrue(
            note != null && note.isNotBlank(),
            "the answer carries no note, so nothing in it says what happened: ${toolText()}",
        )
    }

    /** The log is the only place authorship of a write appears, and it holds what the write was. */
    private suspend fun McpServerHarness.assertLoggedAsApplied(operation: String) {
        val entry = activity.observeAll().first().first { it.operation == operation }

        assertEquals(
            AgentActivity.Outcome.APPLIED,
            entry.outcome,
            "the log holds a refusal for a write that reached the ledger",
        )
    }

    // ----------------------------------------------------------------------------------
    // The world, with three writes whose postings have no leg to be read through
    // ----------------------------------------------------------------------------------

    /**
     * The production server over the production registry, with only the three writes rewired to use
     * cases that succeed and answer with a posting no perspective can read.
     */
    private suspend fun withUnreadablePostings(
        block: suspend (RegistrationWorld, McpServerHarness, McpConversation) -> Unit,
    ) {
        AgentWorld().use { world ->
            val seeded = world.seedRegistration()

            val unreadable = listOf(
                CreateTransactionTool(
                    clock = world.clock,
                    accountRepository = world.accountRepository,
                    creditCardRepository = world.creditCardRepository,
                    categoryRepository = world.categoryRepository,
                    installmentRepository = world.installmentRepository,
                    invoiceRepository = world.invoiceRepository,
                    registerTransaction = WritesOnePostingWithNoLeg,
                ),
                UpdateTransactionTool(
                    transactionRepository = world.transactionRepository,
                    accountRepository = world.accountRepository,
                    creditCardRepository = world.creditCardRepository,
                    categoryRepository = world.categoryRepository,
                    installmentRepository = world.installmentRepository,
                    invoiceRepository = world.invoiceRepository,
                    updateTransaction = RewritesToAPostingWithNoLeg,
                ),
                CreateInstallmentTool(
                    clock = world.clock,
                    creditCardRepository = world.creditCardRepository,
                    categoryRepository = world.categoryRepository,
                    installmentRepository = world.installmentRepository,
                    invoiceRepository = world.invoiceRepository,
                    addInstallment = WritesSharesWithNoLeg,
                ),
            ).associateBy { it.name }

            val port = freePort()

            McpServerHarness(tools = world.tools().map { unreadable[it.name] ?: it }).use { harness ->
                harness.controller.setPort(port)
                harness.controller.setEnabled(true)
                val token = requireNotNull(harness.controller.token.value) { "the server minted no token" }

                withContext(Dispatchers.IO) {
                    block(seeded, harness, McpConversation(port, token).open())
                }

                harness.controller.stop()
            }
        }
    }
}

/** What `AgentActivityJournal` answers with when a tool throws instead of refusing. */
private const val GENERIC_FAILURE = "The operation could not be completed."

private val WRITTEN_ON = LocalDate(2026, 3, 14)

/**
 * A posting the ledger holds and no perspective can read: no monetary leg, so `legUnder` answers
 * `null` with a perspective and `Transaction.primaryEntry` answers `null` without one.
 */
private fun legless(id: Long, title: String?) = Transaction(
    id = id,
    title = title,
    date = WRITTEN_ON,
    entries = emptyList(),
)

private object WritesOnePostingWithNoLeg : RegisterTransactionUseCase {
    override suspend fun invoke(
        form: TransactionForm,
        isRecurring: Boolean,
    ): Either<Throwable, TransactionRegistration> =
        TransactionRegistration.Single(legless(id = 900, title = form.title)).right()
}

private object RewritesToAPostingWithNoLeg : UpdateTransactionUseCase {
    override suspend fun invoke(
        transactionId: Long,
        form: TransactionForm,
    ): Either<Throwable, Transaction> = legless(id = transactionId, title = form.title).right()
}

private object WritesSharesWithNoLeg : AddInstallmentUseCase {
    override suspend fun invoke(
        form: TransactionForm,
        installments: Int,
    ): Either<Throwable, List<Transaction>> = List(installments) {
        legless(id = 910L + it, title = form.title)
    }.right()
}
