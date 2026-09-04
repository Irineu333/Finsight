package com.neoutils.finsight.mcp

import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.ExchangeRate
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.model.RecurringOccurrence
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.feature.mcp.api.AgentActivity
import com.neoutils.finsight.mcp.McpServerHarness.Companion.freePort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The operations family, driven **over the wire** against a real ledger.
 *
 * The same two things are checked of every tool here as of the registration family — something
 * changed, and the log holds one entry saying who changed it — with one addition that belongs to
 * this family alone: an operation's effect is a *state*, so what is asserted is the state the app is
 * left in, read back from the ledger and the stores, never the payload echoing an argument.
 */
class OperationsFamilyOverTheProtocolTest {

    // ------------------------------------------------------------------------------
    // 11.2 — paying a bill has two consequences, and a test that checks one is useless
    // ------------------------------------------------------------------------------

    /**
     * **Paying an invoice writes the payment *and* settles the invoice, and both are asserted.**
     *
     * This is the family's whole reason for existing as a separate group. `PayInvoiceUseCase` and
     * `PayInvoicePaymentUseCase` are near-namesakes; the first writes `status = PAID` and nothing
     * else. A tool wired to it would mark the bill settled with the money still in the account —
     * the balance would then lie, the card's `LIABILITY` legs would stay standing, and **nothing
     * would fail**.
     *
     * So the status is deliberately *not* the assertion. The two that matter are below it: the
     * payment posting exists in the ledger, with a leg on the paying account and a leg carrying the
     * invoice's dimension, and the paying account's balance fell by what was owed. A test that
     * checked only the status would pass against the wrong use case, which is the definition of a
     * test that proves nothing.
     */
    @Test
    fun `paying an invoice posts the payment and moves the paying account's balance`() = runTest {
        withOperationsWorld { world, client ->
            val before = world.entryRepository.balance(world.checkingId)
            assertEquals(Operations.BALANCE_BEFORE, before, "the fixture did not fund the account")

            val response = client.callTool(
                "pay_invoice",
                """{"id":${world.closedInvoiceId},"account_id":${world.checkingId},"date":"2026-02-25"}""",
            )

            assertTrue(!response.isToolError(), "the payment was refused: ${response.toolText()}")

            // --- Consequence one: the payment exists, as a posting, with both its ends ---------
            val invoice = assertNotNull(world.invoiceRepository.getInvoiceById(world.closedInvoiceId))
            val payment = world.transactionRepository.getAllTransactions()
                .filter { it.date == LocalDate(2026, 2, 25) }
                .singleOrNull { transaction ->
                    transaction.entries.any { it.account.id == world.checkingId }
                }

            assertNotNull(
                payment,
                "no payment was posted — the invoice was marked paid and the money never left",
            )
            assertEquals(
                -(Operations.CLOSED_INVOICE_OWED * CENTS).toLong(),
                payment.entries.single { it.account.id == world.checkingId }.amount,
                "the payment's account leg is not what the invoice owed",
            )
            assertEquals(
                invoice.dimensionId,
                payment.entries.single { it.account.id == world.cardAccountId }.dimensionId,
                "the payment's card leg does not carry the invoice's dimension",
            )

            // --- Consequence two: the account is poorer by exactly that ----------------------
            assertEquals(
                Operations.BALANCE_AFTER_PAYING,
                world.entryRepository.balance(world.checkingId),
                "the paying account's balance did not move: the bill was settled with no money",
            )
            assertEquals(
                0.0,
                world.entryRepository.dimensionOwedByCurrency(invoice.dimensionId!!)
                    .singleOrNull()?.value ?: 0.0,
                "the invoice still owes something after being paid in full",
            )

            // The status is the third fact, and deliberately the last: it is the one a wrong
            // implementation would also produce.
            assertEquals(Invoice.Status.PAID, invoice.status)
            assertEquals(LocalDate(2026, 2, 25), invoice.paidAt)

            val entry = world.activityOf(client).single()
            assertEquals(AgentActivity.Outcome.APPLIED, entry.outcome)
            assertEquals("pay_invoice", entry.operation)
            assertEquals(AgentActivity.Reference.Kind.INVOICE, entry.reference?.kind)
        }
    }

    // ------------------------------------------------------------------------------
    // 11.7 — the rate of a crossing is harvested, never received
    // ------------------------------------------------------------------------------

    /**
     * **A transfer between accounts in different currencies harvests its own rate, and is given
     * none.**
     *
     * Two halves, and both are needed. The first is that no rate can be *stated*: the tool's schema
     * offers no such argument, so the call below could not carry one even if an agent wanted to.
     * The second is that a rate was nevertheless **learned** — the quotient of the two ends the
     * caller did state — and written to the archive with the pair and the direction the operation
     * happened in.
     *
     * The distinction is the surface's third withheld capability read from the other side: the agent
     * may not write a rate, and the app still ends up knowing one, because the rate is a fact the
     * operation already contained rather than an opinion anybody supplied.
     */
    @Test
    fun `a transfer across currencies harvests the rate from its own two ends`() = runTest {
        withOperationsWorld { world, client ->
            val transfer = world.world.tool(McpToolName.TRANSFER)

            // Half one: there is nowhere to put a rate.
            val parameters = assertNotNull(transfer.inputSchema.properties).keys
            assertEquals(
                emptyList(),
                parameters.filter { "rate" in it.lowercase() },
                "`transfer` takes a rate as an argument: $parameters",
            )

            val response = client.callTool(
                "transfer",
                """
                {"from_account_id":${world.checkingId},"to_account_id":${world.dollarsId},
                 "amount":550.00,"destination_amount":100.00,"date":"2026-03-12"}
                """.trimIndent().replace("\n", ""),
            )

            assertTrue(!response.isToolError(), "the transfer was refused: ${response.toolText()}")

            // Half two: a rate was learned anyway, and it is the quotient of the two ends.
            val harvested: ExchangeRate = world.exchangeRates.saved.single()

            assertEquals("BRL", harvested.currency)
            assertEquals("USD", harvested.counterCurrency)
            assertEquals(
                DESTINATION / SOURCE,
                harvested.rate,
                "the rate is not the quotient of the two ends",
            )
            assertEquals(LocalDate(2026, 3, 12), harvested.date)
            assertEquals(ExchangeRate.Source.DERIVED, harvested.source)

            // Nothing the call carried *is* the rate: it was derived, not relayed.
            assertNotEquals(SOURCE, harvested.rate)
            assertNotEquals(DESTINATION, harvested.rate)

            // And the ledger holds both ends as the user stated them, each in its own currency,
            // with the residue of the crossing placed by the write boundary.
            val posted = world.transactionRepository.getAllTransactions()
                .single { it.date == LocalDate(2026, 3, 12) }
            assertEquals(
                -(SOURCE * CENTS).toLong(),
                posted.entries.single { it.account.id == world.checkingId }.amount,
            )
            assertEquals(
                (DESTINATION * CENTS).toLong(),
                posted.entries.single { it.account.id == world.dollarsId }.amount,
            )
            posted.entries.groupBy { it.currency }.forEach { (currency, legs) ->
                assertEquals(0L, legs.sumOf { it.amount }, "$currency does not balance")
            }

            assertEquals(1, world.activityOf(client).size)
        }
    }

    /**
     * **A transfer is named by what the call said, and by nothing when it said nothing.**
     *
     * The title is the domain's own parameter — *why the money moved, as the user stated it* — and
     * a tool that cannot carry it drops the one thing the sentence the agent was given actually
     * added. Nothing refuses such a call and the money moves correctly; what the person finds later
     * is a posting with no indication of what it was for, and the way back into the app is closed
     * to the agent, because `update_transaction` refuses transfers.
     *
     * The blank half is the decision the surface already takes everywhere: what arrives empty said
     * nothing. A posting being created has no name to take back, so `""` is a transfer with nothing
     * stated rather than one named with spaces.
     */
    @Test
    fun `a transfer is titled by what the call stated, and by nothing when it stated nothing`() = runTest {
        withOperationsWorld { world, client ->
            val titled = client.callTool(
                "transfer",
                """
                {"from_account_id":${world.checkingId},"to_account_id":${world.savingsId},
                 "amount":150.00,"date":"2026-03-10","title":"$TRANSFER_TITLE"}
                """.trimIndent().replace("\n", ""),
            )
            assertTrue(!titled.isToolError(), "the transfer was refused: ${titled.toolText()}")

            val blank = client.callTool(
                "transfer",
                """
                {"from_account_id":${world.checkingId},"to_account_id":${world.savingsId},
                 "amount":150.00,"date":"2026-03-11","title":"   "}
                """.trimIndent().replace("\n", ""),
            )
            assertTrue(!blank.isToolError(), "the transfer was refused: ${blank.toolText()}")

            val posted = world.transactionRepository.getAllTransactions()

            assertEquals(
                TRANSFER_TITLE,
                posted.single { it.date == LocalDate(2026, 3, 10) }.title,
                "the transfer was recorded without the reason the call gave for it",
            )
            assertNull(
                posted.single { it.date == LocalDate(2026, 3, 11) }.title,
                "a blank title is a transfer with nothing stated, not one named with spaces",
            )
        }
    }

    // ------------------------------------------------------------------------------
    // Correcting an operation, which is the operation restated and not a second one
    // ------------------------------------------------------------------------------

    /**
     * **A transfer the agent corrects is the same operation afterwards, with both ends moved.**
     *
     * That is the whole distinction between correcting and removing-then-registering, and it is
     * asserted the only way it can be: the ledger holds no second posting, and the identity the
     * correction answers with is the one the registration produced.
     *
     * The second half is what a carried field means on a tool with no form. The call below names an
     * amount and nothing else, so the title and the date are the operation's own — a correction
     * that blanked them would be taking away what the agent never mentioned, and reporting it as
     * kept.
     */
    @Test
    fun `correcting a transfer rewrites both ends and keeps the operation`() = runTest {
        withOperationsWorld { world, client ->
            val registered = client.callTool(
                "transfer",
                """
                {"from_account_id":${world.checkingId},"to_account_id":${world.savingsId},
                 "amount":150.00,"date":"2026-03-10","title":"$TRANSFER_TITLE"}
                """.trimIndent().replace("\n", ""),
            )
            assertTrue(!registered.isToolError(), "the transfer was refused: ${registered.toolText()}")

            val id = registered.payload().at("transaction").identity()
            val postings = world.transactionRepository.getAllTransactions().size

            val corrected = client.callTool("update_transfer", """{"id":$id,"amount":200.00}""")

            assertTrue(!corrected.isToolError(), "the correction was refused: ${corrected.toolText()}")

            // One operation, and the same one: the legs were rewritten under it.
            assertEquals(
                postings,
                world.transactionRepository.getAllTransactions().size,
                "correcting left a second operation behind",
            )
            assertEquals(id, corrected.payload().at("transaction").identity())

            // Both ends moved to the corrected figure.
            assertEquals(
                Operations.BALANCE_BEFORE - 200.0,
                world.entryRepository.balance(world.checkingId),
                "the source end was not rewritten",
            )
            assertEquals(
                200.0,
                world.entryRepository.balance(world.savingsId),
                "the destination end was not rewritten",
            )

            // And what the call did not name is what it was.
            val posting = assertNotNull(world.transactionRepository.getTransactionById(id))
            assertEquals(TRANSFER_TITLE, posting.title, "the correction erased a title nobody named")
            assertEquals(LocalDate(2026, 3, 10), posting.date, "the correction moved a date nobody named")

            assertEquals(2, world.activityOf(client).size)
        }
    }

    /**
     * **A partial payment is corrected against what the invoice would owe without it.**
     *
     * The ceiling is the point. This payment already reduced the invoice, so a ceiling counting it
     * would refuse the very correction that raises the figure — the invoice owes 150 with the
     * payment on it and 200 without, and correcting to 200 has to be admissible while 250 does not.
     *
     * The mode is not redecided either: what comes back is still a partial payment, so the cycle is
     * left open and the invoice unsettled even where the correction takes it to zero.
     */
    @Test
    fun `correcting a partial payment is judged by what the invoice owes without it`() = runTest {
        withOperationsWorld { world, client ->
            val registered = client.callTool(
                "advance_invoice_payment",
                """{"id":${world.openInvoiceId},"amount":50.00,"account_id":${world.checkingId},
                   "date":"2026-03-10"}""".trimIndent().replace("\n", ""),
            )
            assertTrue(!registered.isToolError(), "the advance was refused: ${registered.toolText()}")

            val id = registered.payload().at("transaction").identity()
            val postings = world.transactionRepository.getAllTransactions().size

            // Past the whole of the invoice, ceiling included: refused.
            val beyond = client.callTool(
                "update_advance_invoice_payment",
                """{"id":$id,"amount":250.00}""",
            )
            assertTrue(beyond.isToolError(), "a correction past what the invoice owes was accepted")

            val corrected = client.callTool(
                "update_advance_invoice_payment",
                """{"id":$id,"amount":${Operations.OPEN_INVOICE_OWED}}""",
            )

            assertTrue(!corrected.isToolError(), "the correction was refused: ${corrected.toolText()}")

            assertEquals(
                postings,
                world.transactionRepository.getAllTransactions().size,
                "correcting left a second payment behind",
            )
            assertEquals(id, corrected.payload().at("transaction").identity())

            assertEquals(
                Operations.BALANCE_BEFORE - Operations.OPEN_INVOICE_OWED,
                world.entryRepository.balance(world.checkingId),
                "the paying account did not part with the corrected figure",
            )
            assertEquals(
                0.0,
                corrected.payload().at("invoice", "owed").amount(),
                "the invoice does not read as paid down to nothing",
            )
            assertEquals(
                Invoice.Status.OPEN,
                assertNotNull(world.invoiceRepository.getInvoiceById(world.openInvoiceId)).status,
                "correcting a partial payment settled the cycle",
            )
        }
    }

    /**
     * **Every correction tool names the one that corrects what it was handed instead.**
     *
     * A refusal that only says no teaches the agent to invent a way round it, and here there is a
     * way that is not invented: which form corrects an operation follows from what the operation is,
     * and each of the three answers the same reading. Asserted in both directions, because a
     * mapping written twice would be free to disagree with itself.
     */
    @Test
    fun `a correction tool handed another kind of operation names the one that corrects it`() = runTest {
        withOperationsWorld { world, client ->
            val purchase = world.transactionRepository.getAllTransactions()
                .single { it.title == MARCH_PURCHASE }

            val transfer = client.callTool(
                "transfer",
                """
                {"from_account_id":${world.checkingId},"to_account_id":${world.savingsId},
                 "amount":150.00,"date":"2026-03-10"}
                """.trimIndent().replace("\n", ""),
            ).payload().at("transaction").identity()

            // A card purchase is the transaction form's, not the transfer form's.
            val asTransfer = client.callTool("update_transfer", """{"id":${purchase.id}}""")
            assertTrue(asTransfer.isToolError(), "a card purchase was accepted as a transfer")
            assertEquals(
                "update_transaction",
                asTransfer.payload().text("try_instead"),
                "the refusal does not name the tool that corrects a purchase",
            )

            // And the way back: the transaction form refuses a transfer, naming the one that takes
            // its two monetary legs.
            val asTransaction = client.callTool("update_transaction", """{"id":$transfer,"amount":10}""")
            assertTrue(asTransaction.isToolError(), "a transfer was accepted by the transaction form")
            assertEquals(
                "update_transfer",
                asTransaction.payload().text("try_instead"),
                "the refusal does not name the tool that corrects a transfer",
            )
        }
    }

    // ------------------------------------------------------------------------------
    // 11.5 — what an omitted title and an omitted category mean to a tool with no form
    // ------------------------------------------------------------------------------

    /**
     * **Confirming a cycle without opining follows the template — title and category included.**
     *
     * The use case resolves an omitted `title` and an omitted `category` to **nothing**, which is
     * right for the sheet that arrives pre-filled and wrong for a tool that has no form. Forwarding
     * the `null` would post the Netflix cycle with no name and no classification, and the agent
     * would report "confirmed" with nothing to suggest otherwise. So the tool pre-fills, and this
     * is what pre-filling has to produce.
     */
    @Test
    fun `confirming a cycle without opining keeps the template's title and category`() = runTest {
        withOperationsWorld { world, client ->
            val response = client.callTool(
                "confirm_recurring",
                """{"id":${world.recurringId},"date":"2026-03-10"}""",
            )

            assertTrue(!response.isToolError(), "the cycle was refused: ${response.toolText()}")

            val posted = world.transactionRepository.getAllTransactions()
                .single { it.recurringId == world.recurringId }

            assertEquals(
                Operations.RECURRING_TITLE,
                posted.title,
                "the cycle was posted with no title of its own",
            )
            assertEquals(
                world.categoryRepository.getCategoryById(world.categoryId)?.dimensionId,
                posted.nominalDimensionId,
                "the cycle was posted uncategorised",
            )
            assertEquals(
                Operations.RECURRING_AMOUNT,
                posted.amount,
                "the cycle was not worth what the template describes",
            )

            // And the agent is told the same thing the ledger holds.
            val answer = response.payload().at("transaction")
            assertEquals(Operations.RECURRING_TITLE, answer.text("title"))
            assertEquals(Operations.CATEGORY_NAME, answer.text("category"))

            assertEquals(
                RecurringOccurrence.Status.CONFIRMED,
                world.occurrences.single().status,
                "the month was not recorded as handled, so it would be offered again",
            )
            assertEquals(1, world.activityOf(client).size)
        }
    }

    /**
     * **And erasing stays expressible**, because the sheet can erase.
     *
     * An empty title and a `category_id` of `0` are the only way a `null` reaches the use case from
     * here, so the tool's pre-filling narrows nothing: a cycle that genuinely had neither is still
     * one call away.
     */
    @Test
    fun `a cycle stated to have neither title nor category is posted with neither`() = runTest {
        withOperationsWorld { world, client ->
            val response = client.callTool(
                "confirm_recurring",
                """{"id":${world.recurringId},"date":"2026-03-10","title":"","category_id":0}""",
            )

            assertTrue(!response.isToolError(), "the cycle was refused: ${response.toolText()}")

            val posted = world.transactionRepository.getAllTransactions()
                .single { it.recurringId == world.recurringId }

            assertNull(posted.title, "the template's title was substituted back in")
            assertNull(posted.nominalDimensionId, "the template's category was substituted back in")
        }
    }

    // ------------------------------------------------------------------------------
    // 11.5b — the one write that reaches the ledger without a form to hold the rule
    // ------------------------------------------------------------------------------

    /**
     * **A cycle classified against its direction is refused, and the refusal names the category,
     * its kind and the direction.**
     *
     * The five tools that build a form already hold `isAccept`. This one builds none: the category
     * it forwards decides the *nature of the contra leg*, so an expense template confirmed under an
     * income category posts `{ASSET −, INCOME +}` — the money leaves the account and the posting
     * reads back as income, with `Σ = 0` intact and the answer saying "Confirmed".
     *
     * So the ledger is what is asserted, never the payload.
     */
    @Test
    fun `a cycle classified against its direction is refused, naming it and both kinds`() = runTest {
        withOperationsWorld { world, client ->
            val salary = world.world.category(
                id = 2,
                dimensionId = 2,
                name = "Salário",
                type = Category.Type.INCOME,
            )
            val before = world.database.entryDao().getAll()

            val response = client.callTool(
                "confirm_recurring",
                """{"id":${world.recurringId},"date":"2026-03-10","category_id":${salary.id}}""",
            )

            assertTrue(
                response.isToolError(),
                "an expense cycle was classified under an income category: ${response.toolText()}",
            )

            val reason = assertNotNull(response.payload().text("reason"))
            listOf("category_id", "Salário", "income", "expense").forEach {
                assertTrue(
                    it in reason,
                    "the refusal does not name `$it`, so the agent cannot tell which end is " +
                        "wrong: $reason",
                )
            }

            assertEquals(
                0,
                world.transactionRepository.getAllTransactions().count {
                    it.recurringId == world.recurringId
                },
                "the cycle was posted, in the direction opposite to the one the money moved",
            )
            assertEquals(before, world.database.entryDao().getAll(), "the ledger moved anyway")
            assertTrue(
                world.occurrences.isEmpty(),
                "the month was recorded as handled by a cycle that never posted",
            )
        }
    }

    /**
     * **The same disagreement reached from the other side: the template carries it, and the call
     * said nothing about the category.**
     *
     * A template stored incoherent before the rule existed has no migration, so this is reachable
     * on real data. Nothing here is an argument the caller gave — the tool pre-fills the category
     * from the template — so the refusal has to say what the *template* holds, or the agent has
     * nothing to change.
     */
    @Test
    fun `a template carrying an incoherent category is refused at the cycle, not posted`() = runTest {
        withOperationsWorld { world, client ->
            val salary = world.world.category(
                id = 2,
                dimensionId = 2,
                name = "Salário",
                type = Category.Type.INCOME,
            )
            val incoherent = Recurring(
                id = 2,
                type = TransactionType.EXPENSE,
                amount = 39.90,
                title = "Legado",
                dayOfMonth = 10,
                category = salary,
                account = world.world.accounts.first { it.id == world.checkingId },
                creditCard = null,
                // The same origin the seeded template has, so the cycle number is the same one
                // this world's March already answers for.
                createdAt = assertNotNull(
                    world.recurringRepository.getRecurringById(world.recurringId),
                ).createdAt,
            ).also { world.world.recurringList += it }

            val before = world.database.entryDao().getAll()

            val response = client.callTool(
                "confirm_recurring",
                """{"id":${incoherent.id},"date":"2026-03-10"}""",
            )

            assertTrue(
                response.isToolError(),
                "the template's own incoherence was carried into the ledger: ${response.toolText()}",
            )

            val reason = assertNotNull(response.payload().text("reason"))
            listOf("Salário", "income", "expense").forEach {
                assertTrue(
                    it in reason,
                    "the refusal does not name `$it`, so the agent cannot tell what is wrong: $reason",
                )
            }

            assertEquals(
                0,
                world.transactionRepository.getAllTransactions().count {
                    it.recurringId == incoherent.id
                },
                "the cycle was posted in the direction opposite to the one the money moved",
            )
            assertEquals(before, world.database.entryDao().getAll(), "the ledger moved anyway")
        }
    }

    /**
     * **The way out both refusals name actually works.**
     *
     * A refusal that tells the agent what to do instead is only as good as that instruction: both
     * of them say `category_id` 0 confirms the cycle unclassified, and an agent that follows the
     * one it was given has to arrive somewhere. So the escape is exercised on the harder of the
     * two — a template whose *own* category is the incoherent one, where the agent changed no
     * argument because it gave none.
     */
    @Test
    fun `the category_id of 0 both refusals name confirms the cycle unclassified`() = runTest {
        withOperationsWorld { world, client ->
            val salary = world.world.category(
                id = 2,
                dimensionId = 2,
                name = "Salário",
                type = Category.Type.INCOME,
            )
            val incoherent = Recurring(
                id = 2,
                type = TransactionType.EXPENSE,
                amount = 39.90,
                title = "Legado",
                dayOfMonth = 10,
                category = salary,
                account = world.world.accounts.first { it.id == world.checkingId },
                creditCard = null,
                createdAt = assertNotNull(
                    world.recurringRepository.getRecurringById(world.recurringId),
                ).createdAt,
            ).also { world.world.recurringList += it }

            val response = client.callTool(
                "confirm_recurring",
                """{"id":${incoherent.id},"date":"2026-03-10","category_id":0}""",
            )

            assertTrue(
                !response.isToolError(),
                "the escape the refusal names is itself refused: ${response.toolText()}",
            )

            val posted = world.transactionRepository.getAllTransactions()
                .single { it.recurringId == incoherent.id }

            assertNull(posted.nominalDimensionId, "the template's incoherent category was kept")
            assertEquals(
                -(39.90 * CENTS).toLong(),
                posted.entries.single { it.account.id == world.checkingId }.amount,
                "the cycle did not leave the account: it was posted as income after all",
            )
        }
    }

    // ------------------------------------------------------------------------------
    // 11.6 — the generic tool describes exactly what it accepts
    // ------------------------------------------------------------------------------

    /**
     * **The prose of `archive_entity` and the domain of its discriminator are the same four words.**
     *
     * `mcp-tool-surface` requires it, and the failure it rules out is invisible until a call fails:
     * a description naming a kind the parameter rejects teaches the consumer to distrust the only
     * material it has for choosing.
     */
    @Test
    fun `the generic tools name exactly the kinds their discriminator accepts`() = runTest {
        AgentWorld().use { world ->
            listOf(McpToolName.ARCHIVE_ENTITY, McpToolName.UNARCHIVE_ENTITY).forEach { name ->
                val tool = world.tool(name)
                val accepted = assertNotNull(tool.inputSchema.properties)["type"]!!
                    .jsonObject["enum"]!!
                    .jsonArray
                    .map { it.jsonPrimitive.content }
                    .toSet()

                assertEquals(
                    ARCHIVABLE_KINDS,
                    accepted,
                    "${tool.name} accepts a different set of kinds than the surface decided on",
                )

                val named = ARCHIVABLE_KINDS.filter { it in tool.description }.toSet()
                assertEquals(
                    accepted,
                    named,
                    "${tool.name} describes a different set of kinds than it accepts: " +
                        tool.description,
                )
            }
        }
    }

    /** All four kinds go out of circulation and come back, through one pair of tools. */
    @Test
    fun `all four kinds archive and unarchive through the generic pair`() = runTest {
        withOperationsWorld { world, client ->
            // The card's bill has to be settled before it can be retired, and the account must not
            // be the default one: both are the domain's guards, not the tool's.
            client.callTool(
                "pay_invoice",
                """{"id":${world.closedInvoiceId},"account_id":${world.checkingId},"date":"2026-02-25"}""",
            )
            client.callTool("adjust_invoice", """{"id":${world.openInvoiceId},"target":0}""")

            val subjects = listOf(
                "account" to world.savingsId,
                "card" to world.cardId,
                "category" to world.categoryId,
                "recurring" to world.recurringId,
            )

            subjects.forEach { (type, id) ->
                val archived = client.callTool("archive_entity", """{"type":"$type","id":$id}""")
                assertTrue(
                    !archived.isToolError(),
                    "archiving the $type was refused: ${archived.toolText()}",
                )
                assertEquals(
                    true,
                    archived.payload().flag("is_archived"),
                    "the $type says it is not archived after being archived",
                )

                val restored = client.callTool("unarchive_entity", """{"type":"$type","id":$id}""")
                assertTrue(
                    !restored.isToolError(),
                    "unarchiving the $type was refused: ${restored.toolText()}",
                )
                assertEquals(
                    false,
                    restored.payload().flag("is_archived"),
                    "the $type says it is still archived after coming back",
                )
            }

            assertEquals(
                setOf(false),
                setOf(
                    world.accountRepository.getAccountById(world.savingsId)!!.isArchived,
                    world.creditCardRepository.getCreditCardById(world.cardId)!!.isArchived,
                    world.categoryRepository.getCategoryById(world.categoryId)!!.isArchived,
                    world.recurringRepository.getRecurringById(world.recurringId)!!.isArchived,
                ),
                "something stayed archived after being brought back",
            )

            // Eight retirements, eight entries that went through, and each one reaching the kind
            // of thing it was about — a log that could not say which of the four would be a log
            // the user cannot follow back.
            val retirements = world.activityOf(client)
                .filter { it.operation in setOf("archive_entity", "unarchive_entity") }

            assertEquals(subjects.size * 2, retirements.size)
            assertTrue(
                retirements.all { it.outcome == AgentActivity.Outcome.APPLIED },
                "a retirement that went through was recorded as refused: $retirements",
            )
            assertEquals(
                setOf(
                    AgentActivity.Reference.Kind.ACCOUNT,
                    AgentActivity.Reference.Kind.CREDIT_CARD,
                    AgentActivity.Reference.Kind.CATEGORY,
                    AgentActivity.Reference.Kind.RECURRING,
                ),
                retirements.mapNotNull { it.reference?.kind }.toSet(),
                "the log cannot say which kind a retirement was about",
            )
        }
    }

    /** A kind the discriminator does not accept never reaches a use case. */
    @Test
    fun `a kind outside the discriminator is refused by name`() = runTest {
        withOperationsWorld { world, client ->
            val response = client.callTool("archive_entity", """{"type":"budget","id":1}""")

            assertTrue(response.isToolError(), "an unlisted kind was accepted")
            assertTrue(
                ARCHIVABLE_KINDS.all { it in response.payload().text("reason").orEmpty() },
                "the refusal does not say what is accepted: ${response.toolText()}",
            )
            assertEquals(
                AgentActivity.Outcome.REFUSED,
                world.activityOf(client).single().outcome,
            )
        }
    }

    // ------------------------------------------------------------------------------
    // 11.3 / 11.4 — the rest of the family, each over the wire and into the ledger
    // ------------------------------------------------------------------------------

    /** Part of an open bill is settled early, and the cycle stays open. */
    @Test
    fun `an advance payment moves money and leaves the cycle open`() = runTest {
        withOperationsWorld { world, client ->
            val response = client.callTool(
                "advance_invoice_payment",
                """{"id":${world.openInvoiceId},"amount":50.00,"account_id":${world.checkingId},
                   "date":"2026-03-10"}""".trimIndent().replace("\n", ""),
            )

            assertTrue(!response.isToolError(), "the advance was refused: ${response.toolText()}")

            assertEquals(
                Operations.BALANCE_BEFORE - 50.0,
                world.entryRepository.balance(world.checkingId),
                "the paying account did not part with the money",
            )
            assertEquals(
                Invoice.Status.OPEN,
                world.invoiceRepository.getInvoiceById(world.openInvoiceId)!!.status,
                "paying part of a cycle closed it",
            )
            assertEquals(
                Operations.OPEN_INVOICE_OWED - 50.0,
                response.payload().at("invoice", "owed").amount(),
                "the answer does not carry what is left on the invoice",
            )
            assertEquals(1, world.activityOf(client).size)
        }
    }

    /** Closing a cycle opens its successor, and settles nothing. */
    @Test
    fun `closing a cycle opens the next one and leaves the debt standing`() = runTest {
        withOperationsWorld { world, client ->
            val response = client.callTool(
                "close_invoice",
                """{"id":${world.openInvoiceId},"date":"2026-03-15"}""",
            )

            assertTrue(!response.isToolError(), "the close was refused: ${response.toolText()}")

            val closed = world.invoiceRepository.getInvoiceById(world.openInvoiceId)!!
            assertEquals(Invoice.Status.CLOSED, closed.status, "an owing cycle was settled by closing")
            assertEquals(
                YearMonth(2026, 3),
                world.invoiceRepository.getOpenInvoice(world.cardId)?.openingMonth,
                "closing did not open the successor",
            )
            assertEquals(
                Operations.OPEN_INVOICE_OWED,
                response.payload().at("invoice", "owed").amount(),
                "closing changed what the cycle owes",
            )
        }
    }

    /** And reopening puts it back, demoting the successor that had taken its place. */
    @Test
    fun `reopening a cycle demotes the successor that opened in its place`() = runTest {
        withOperationsWorld { world, client ->
            val response = client.callTool(
                "reopen_invoice",
                """{"id":${world.closedInvoiceId}}""",
            )

            assertTrue(!response.isToolError(), "the reopen was refused: ${response.toolText()}")

            assertEquals(
                Invoice.Status.OPEN,
                world.invoiceRepository.getInvoiceById(world.closedInvoiceId)!!.status,
            )
            assertEquals(
                Invoice.Status.FUTURE,
                world.invoiceRepository.getInvoiceById(world.openInvoiceId)!!.status,
                "the card was left with two open cycles",
            )
        }
    }

    /** A cycle already declared for the month is **promoted**, never duplicated. */
    @Test
    fun `opening a cycle promotes the one already declared for that month`() = runTest {
        withOperationsWorld { world, client ->
            val before = world.invoiceRepository.getInvoicesByCreditCard(world.cardId).size
            assertEquals(
                Invoice.Status.FUTURE,
                world.invoiceRepository.getInvoiceById(world.futureInvoiceId)!!.status,
                "the fixture holds no declared cycle to promote",
            )

            val response = client.callTool(
                "open_invoice",
                """{"card_id":${world.cardId},"opening_month":"2026-03"}""",
            )

            assertTrue(!response.isToolError(), "the opening was refused: ${response.toolText()}")

            assertEquals(
                Invoice.Status.OPEN,
                world.invoiceRepository.getInvoiceById(world.futureInvoiceId)!!.status,
                "the declared cycle was not the one put on the air",
            )
            assertEquals(
                before,
                world.invoiceRepository.getInvoicesByCreditCard(world.cardId).size,
                "a second cycle was created for a month that already had one",
            )
            assertEquals(
                world.futureInvoiceId,
                response.payload().at("invoice").identity(),
                "the answer names a cycle other than the one that opened",
            )
        }
    }

    /** Correcting an invoice posts the difference, and correcting twice does not accumulate. */
    @Test
    fun `adjusting an invoice posts the difference and re-adjusting lands on the target`() = runTest {
        withOperationsWorld { world, client ->
            val first = client.callTool(
                "adjust_invoice",
                """{"id":${world.openInvoiceId},"target":250.00,"date":"2026-03-12"}""",
            )
            assertTrue(!first.isToolError(), "the correction was refused: ${first.toolText()}")
            assertEquals(250.00, first.payload().at("invoice", "owed").amount())

            val again = client.callTool(
                "adjust_invoice",
                """{"id":${world.openInvoiceId},"target":180.00,"date":"2026-03-12"}""",
            )
            assertTrue(!again.isToolError(), "the second correction was refused: ${again.toolText()}")
            assertEquals(
                180.00,
                again.payload().at("invoice", "owed").amount(),
                "the second correction accumulated onto the first instead of replacing it",
            )
            assertEquals(2, world.activityOf(client).size)
        }
    }

    /** Correcting a balance posts the difference against an equity counter-leg. */
    @Test
    fun `adjusting a balance posts the difference and the account then holds the target`() = runTest {
        withOperationsWorld { world, client ->
            val response = client.callTool(
                "adjust_balance",
                """{"account_id":${world.checkingId},"target_balance":900.00,"date":"2026-03-12"}""",
            )

            assertTrue(!response.isToolError(), "the correction was refused: ${response.toolText()}")

            assertEquals(
                900.00,
                world.entryRepository.balance(world.checkingId),
                "the account does not hold what it was corrected to",
            )
            assertEquals(900.00, response.payload().at("account", "balance").amount())

            val adjustment = world.transactionRepository.getAllTransactions()
                .single { it.date == LocalDate(2026, 3, 12) }
            assertEquals(
                -(100 * CENTS).toLong(),
                adjustment.entries.single { it.account.id == world.checkingId }.amount,
                "the correction posted something other than the difference",
            )
        }
    }

    /** The role is exclusive: electing one demotes whoever held it. */
    @Test
    fun `electing a default account demotes the one that held the role`() = runTest {
        withOperationsWorld { world, client ->
            val response = client.callTool(
                "set_default_account",
                """{"id":${world.savingsId}}""",
            )

            assertTrue(!response.isToolError(), "the election was refused: ${response.toolText()}")
            assertEquals(true, response.payload().at("account").flag("is_default"))
            assertEquals(
                listOf(world.savingsId),
                world.accountRepository.getAllAccounts().filter { it.isDefault }.map { it.id },
                "the app was left with something other than exactly one default",
            )
        }
    }

    /** A skip writes no posting: the month simply stops being offered. */
    @Test
    fun `skipping a cycle records the pass and posts nothing`() = runTest {
        withOperationsWorld { world, client ->
            val postingsBefore = world.transactionRepository.getAllTransactions().size

            val response = client.callTool(
                "skip_recurring",
                """{"id":${world.recurringId},"date":"2026-03-10"}""",
            )

            assertTrue(!response.isToolError(), "the skip was refused: ${response.toolText()}")

            assertEquals(
                postingsBefore,
                world.transactionRepository.getAllTransactions().size,
                "skipping a cycle wrote a posting",
            )
            assertEquals(
                RecurringOccurrence.Status.SKIPPED,
                world.occurrences.single().status,
            )
            assertEquals("2026-03", response.payload().text("month"))
        }
    }

    // ------------------------------------------------------------------------------
    // The family as a whole: every one of them records what it attempted
    // ------------------------------------------------------------------------------

    /**
     * **Every operation leaves an entry, refusals included.**
     *
     * Each call below names an identity that matches nothing, so each is refused — which is the
     * point: what is being swept is that the attempt is *recorded*, and a refusal is exactly what
     * the log exists to explain. The arguments still satisfy each schema, so the call reaches the
     * tool rather than being turned away before it.
     */
    @Test
    fun `every operation records the attempt, whatever the outcome`() = runTest {
        withOperationsWorld { world, client ->
            val operations = McpToolName.entries.filter { it.family == McpToolFamily.OPERATIONS }

            assertEquals(
                MISSING_IDENTITY.keys,
                operations.toSet(),
                "an operation has no call written for it, so the sweep below skips it",
            )

            operations.forEach { name ->
                val response = client.callTool(name.wireName, MISSING_IDENTITY.getValue(name))
                assertTrue(
                    response.isToolError(),
                    "${name.wireName} accepted an identity that matches nothing: " +
                        response.toolText(),
                )
            }

            val log = world.activityOf(client)
            assertEquals(
                operations.map { it.wireName }.toSet(),
                log.map { it.operation }.toSet(),
                "an operation left no trace of having been attempted",
            )
            assertTrue(
                log.all { it.outcome == AgentActivity.Outcome.REFUSED && !it.detail.isNullOrBlank() },
                "an entry does not say why the attempt was refused: $log",
            )
        }
    }

    /**
     * A world with two card cycles, three accounts and a pending template, a real server over a
     * real socket, and the activity log the operations land in.
     */
    private suspend fun withOperationsWorld(
        block: suspend (OperationsWorld, McpConversation) -> Unit,
    ) {
        AgentWorld().use { world ->
            val seeded = world.seedOperations()

            val port = freePort()
            McpServerHarness(tools = world.tools()).use { harness ->
                harness.controller.setPort(port)
                harness.controller.setEnabled(true)
                val token = assertNotNull(harness.controller.token.value, "the server minted no token")

                withContext(Dispatchers.IO) {
                    block(seeded.copy(harness = harness), McpConversation(port, token).open())
                }

                harness.controller.stop()
            }
        }
    }

    private companion object {

        const val CENTS = 100.0

        /** The two ends of the crossing, in their own currencies. Neither of them is the rate. */
        const val SOURCE = 550.00
        const val DESTINATION = 100.00

        /** Why the money moved, as the person said it — the sentence the call has to carry. */
        const val TRANSFER_TITLE = "Reserva da viagem"

        /** The card purchase the fixture seeds on the open cycle, named so a test can find it. */
        const val MARCH_PURCHASE = "Compra de março"

        /** What the generic pair operates on — the surface's decision, restated for the assertion. */
        val ARCHIVABLE_KINDS = setOf("account", "card", "category", "recurring")

        /**
         * The smallest call each operation accepts, aimed at an identity that matches nothing.
         *
         * Compared against the family in the sweep above, so a tool added to the family without a
         * call here fails rather than being quietly skipped.
         */
        val MISSING_IDENTITY: Map<McpToolName, String> = mapOf(
            McpToolName.PAY_INVOICE to """{"id":9999,"account_id":1}""",
            McpToolName.ADVANCE_INVOICE_PAYMENT to """{"id":9999,"amount":10,"account_id":1}""",
            McpToolName.UPDATE_ADVANCE_INVOICE_PAYMENT to """{"id":9999,"amount":10}""",
            McpToolName.CLOSE_INVOICE to """{"id":9999}""",
            McpToolName.OPEN_INVOICE to """{"card_id":9999,"opening_month":"2026-06"}""",
            McpToolName.REOPEN_INVOICE to """{"id":9999}""",
            McpToolName.ADJUST_INVOICE to """{"id":9999,"target":10}""",
            McpToolName.ADJUST_BALANCE to """{"account_id":9999,"target_balance":10}""",
            McpToolName.TRANSFER to """{"from_account_id":9999,"to_account_id":1,"amount":10}""",
            McpToolName.UPDATE_TRANSFER to """{"id":9999,"amount":10}""",
            McpToolName.SET_DEFAULT_ACCOUNT to """{"id":9999}""",
            McpToolName.CONFIRM_RECURRING to """{"id":9999}""",
            McpToolName.SKIP_RECURRING to """{"id":9999}""",
            McpToolName.ARCHIVE_ENTITY to """{"type":"account","id":9999}""",
            McpToolName.UNARCHIVE_ENTITY to """{"type":"account","id":9999}""",
        )
    }
}
