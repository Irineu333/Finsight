package com.neoutils.finsight.mcp

import arrow.core.Either
import com.neoutils.finsight.domain.error.InvoiceException
import com.neoutils.finsight.domain.model.Installment
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.usecase.DeleteFutureInvoiceUseCase
import com.neoutils.finsight.domain.usecase.DeleteInstallmentUseCase
import com.neoutils.finsight.domain.usecase.DeleteTransactionUseCase
import com.neoutils.finsight.feature.backup.api.PreventiveCaptureException
import com.neoutils.finsight.mcp.McpServerHarness.Companion.freePort
import com.neoutils.finsight.mcp.tool.DeleteInstallmentTool
import com.neoutils.finsight.mcp.tool.DeleteInvoiceTool
import com.neoutils.finsight.mcp.tool.DeleteTransactionTool
import com.neoutils.finsight.util.UiText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * **A copy that could not be taken reaches the agent as the refusal it is.**
 *
 * The preventive vault stands in front of every removal, and when it cannot write the copy it owes
 * it refuses by throwing — which the app's own use cases let escape on purpose, because the only
 * answer to that question is a person's.
 *
 * What must not happen is the agent being handed *"the operation could not be completed"*. The
 * removal was stopped by a safeguard the user switched on, and nothing in that sentence says so: an
 * agent told only that the operation failed looks for the fault in the posting it was asked to
 * remove, and reports the data as broken. The removal staying refused is the vault working; the
 * silence about *why* is the defect.
 *
 * The three tools are exercised over the wire, against the production registry with only the
 * removals rewired, because the refusal has to survive serialisation and arrive flagged as an error
 * the agent must read.
 */
class PreventiveCaptureReachesTheAgentTest {

    @Test
    fun `a posting the vault stopped is refused in words that name the copy`() = runTest {
        withFailingVault { seeded, _, client ->
            val response = client.callTool("delete_transaction", """{"id":${seeded.groceriesId}}""")

            response.assertNamesTheFailedCopy()
        }
    }

    @Test
    fun `a plan the vault stopped is refused in words that name the copy`() = runTest {
        withFailingVault { _, _, client ->
            val response = client.callTool("delete_installment", """{"id":$INSTALLMENT_ID}""")

            response.assertNamesTheFailedCopy()
        }
    }

    @Test
    fun `an invoice the vault stopped is refused in words that name the copy`() = runTest {
        withFailingVault { _, _, client ->
            val response = client.callTool("delete_invoice", """{"id":$FUTURE_INVOICE_ID}""")

            response.assertNamesTheFailedCopy()
        }
    }

    /**
     * The refusal is the vault's, so it says what the vault said — the sentence the person would
     * have been shown — rather than a second wording of it maintained here.
     */
    @Test
    fun `the refusal carries the reason the vault gave`() = runTest {
        withFailingVault { seeded, _, client ->
            val response = client.callTool("delete_transaction", """{"id":${seeded.groceriesId}}""")

            assertTrue(
                VAULT_REASON in assertNotNull(response.payload().text("reason")),
                "the agent is told a copy failed but not what the vault said about it",
            )
        }
    }

    /**
     * A refusal is an act the log holds, and what the person came to it for is *what the agent tried
     * to remove*. The journal's catch-all has no summary to put there and falls back on the
     * operation's own name, which answers a question nobody asked.
     */
    @Test
    fun `the log keeps what the agent tried to remove, not the tool's own name`() = runTest {
        withFailingVault { seeded, harness, client ->
            client.callTool("delete_transaction", """{"id":${seeded.groceriesId}}""")

            val entry = harness.activity.observeAll().first().single()

            assertEquals("delete_transaction", entry.operation)
            assertNotEquals(
                "delete_transaction",
                entry.summary,
                "the tool's name stood in for the posting the agent tried to remove",
            )
            assertTrue(
                "Mercado" in entry.summary,
                "the log does not say what the removal was about: `${entry.summary}`",
            )
        }
    }

    /** Refused means refused: the vault stopped the removal, and the posting is still there. */
    @Test
    fun `nothing is removed while the copy is owed`() = runTest {
        withFailingVault { seeded, _, client ->
            client.callTool("delete_transaction", """{"id":${seeded.groceriesId}}""")

            assertNotNull(
                seeded.transactionRepository.getTransactionById(seeded.groceriesId),
                "the posting went even though the copy owed before it never landed",
            )
        }
    }

    // ----------------------------------------------------------------------------------
    // The world, with a vault that cannot write
    // ----------------------------------------------------------------------------------

    private fun RawHttp.Response.assertNamesTheFailedCopy() {
        assertTrue(isToolError(), "a removal the vault stopped has to arrive as an error")

        assertNotEquals(
            GENERIC_FAILURE,
            toolText(),
            "the vault's refusal fell through to the journal's catch-all, which says nothing " +
                "about a copy, a vault or a backup",
        )

        val reason = assertNotNull(payload().text("reason"), "the refusal carries no reason")

        assertTrue(
            NAMES_THE_COPY.any { it in reason.lowercase() },
            "the refusal never mentions the copy that could not be taken: `$reason`",
        )
    }

    /**
     * The production server over the production registry, with only the three removals rewired to a
     * vault that cannot write. Everything else is what the desktop announces, so what is asserted is
     * the tool as it is registered rather than a copy of it written here.
     */
    private suspend fun withFailingVault(
        block: suspend (RegistrationWorld, McpServerHarness, McpConversation) -> Unit,
    ) {
        AgentWorld().use { world ->
            val seeded = world.seedRegistration()

            world.installments += Installment(id = INSTALLMENT_ID, count = 3, totalAmount = 300.0)
            world.invoice(
                id = FUTURE_INVOICE_ID,
                dimensionId = FUTURE_INVOICE_DIMENSION,
                card = world.cards.first(),
                month = YearMonth(2026, 8),
                status = Invoice.Status.FUTURE,
            )

            val refusing = listOf(
                DeleteTransactionTool(world.transactionRepository, RefusingDeleteTransaction),
                DeleteInstallmentTool(world.installmentRepository, RefusingDeleteInstallment),
                DeleteInvoiceTool(world.invoiceRepository, RefusingDeleteFutureInvoice),
            ).associateBy { it.name }

            val port = freePort()

            McpServerHarness(tools = world.tools().map { refusing[it.name] ?: it }).use { harness ->
                harness.controller.setPort(port)
                harness.controller.setEnabled(true)
                val token = assertNotNull(harness.controller.token.value, "the server minted no token")

                withContext(Dispatchers.IO) {
                    block(seeded, harness, McpConversation(port, token).open())
                }

                harness.controller.stop()
            }
        }
    }
}

private const val INSTALLMENT_ID = 1L
private const val FUTURE_INVOICE_ID = 2L
private const val FUTURE_INVOICE_DIMENSION = 20L

/** What `AgentActivityJournal` answers with when a tool throws instead of refusing. */
private const val GENERIC_FAILURE = "The operation could not be completed."

/**
 * The sentence the vault refused with — `BackupError.EXPORT_FAILED`'s own words, carried verbatim
 * because that error type is what a failed capture is built from. Like every message there, it ends
 * without a stop: the sentence that quotes it supplies its own.
 */
private const val VAULT_REASON = "The backup file could not be written"

/** Any of these, and the agent has something to look for. None of them, and it has nothing. */
private val NAMES_THE_COPY = listOf("copy", "backup", "vault")

/**
 * The three removals as the app performs them when the copy owed could not be taken: the exception
 * escapes rather than being folded into the `Either`.
 *
 * `DeleteTransactionUseCaseImpl` and `DeleteInstallmentUseCaseImpl` rethrow it deliberately;
 * `DeleteFutureInvoiceUseCaseImpl` never catches it in the first place. Either way it reaches the
 * tool as a throw, which is the only shape this suite is about.
 */
private fun captureFailed(): Nothing = throw PreventiveCaptureException(
    reason = UiText.Raw(VAULT_REASON),
    message = VAULT_REASON,
)

private object RefusingDeleteTransaction : DeleteTransactionUseCase {
    override suspend fun invoke(
        transactionId: Long,
        withoutCopy: Boolean,
    ): Either<Throwable, Unit> = captureFailed()
}

private object RefusingDeleteInstallment : DeleteInstallmentUseCase {
    override suspend fun invoke(
        installmentId: Long,
        withoutCopy: Boolean,
    ): Either<Throwable, Unit> = captureFailed()
}

private object RefusingDeleteFutureInvoice : DeleteFutureInvoiceUseCase {
    override suspend fun invoke(
        invoiceId: Long,
        withoutCopy: Boolean,
    ): Either<InvoiceException, Unit> = captureFailed()
}
