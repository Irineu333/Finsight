package com.neoutils.finsight.mcp

import com.neoutils.finsight.feature.mcp.api.AgentActivity
import com.neoutils.finsight.feature.mcp.api.McpPermissionAxis
import com.neoutils.finsight.mcp.McpServerHarness.Companion.freePort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The registration family, driven **over the wire** against a real ledger.
 *
 * Everything is asserted through the protocol rather than by calling the tools' Kotlin functions:
 * the schema the SDK validates the arguments against, the serialisation of the payload and the error
 * flag a refusal has to arrive with all live there, and a tool that behaved correctly in Kotlin and
 * nowhere else would pass a direct call and fail a client.
 *
 * Two things are checked of **every** write here, and they are what separates this family from the
 * two that read: the ledger changed, and the activity log holds one entry saying who changed it.
 */
class RegistrationFamilyOverTheProtocolTest {

    // ------------------------------------------------------------------------------
    // 10.1 — the instalment split is the use case's decision, and it writes N postings
    // ------------------------------------------------------------------------------

    /**
     * **A form with more than one instalment produces the N postings, and the tool decided none of
     * it.**
     *
     * The dispatch between a plan, a template and a plain posting belongs to
     * `RegisterTransactionUseCase`; a tool reading `installments > 1` for itself would be a second
     * copy of that rule, free to disagree with the sheet's. What is asserted is the outcome: twelve
     * postings in the ledger, each on the invoice of its own month, and one plan naming them.
     */
    @Test
    fun `an instalment form produces the N postings, through the use case that owns the split`() =
        runTest {
            withRegistrationWorld { world, client ->
                val card = world.cards.single()

                val response = client.callTool(
                    "create_transaction",
                    """
                    {"type":"expense","amount":1200.00,"title":"Notebook","card_id":${card.id},
                     "date":"2026-03-10","installments":12}
                    """.trimIndent().replace("\n", ""),
                )

                assertTrue(!response.isToolError(), "the split was refused: ${response.toolText()}")

                val payload = response.payload()
                val written = payload["transactions"]!!.jsonArray

                assertEquals(12, written.size, "the split did not produce twelve postings")
                assertEquals(
                    12,
                    world.transactionRepository.getAllTransactions()
                        .count { it.title == "Notebook" },
                    "the ledger does not hold the twelve postings",
                )
                assertEquals(
                    12,
                    payload.at("installment").count("count"),
                    "the plan does not describe twelve shares",
                )
                assertEquals(
                    12,
                    world.transactionRepository.getAllTransactions()
                        .mapNotNull { it.installmentId }
                        .distinct()
                        .let { plans ->
                            assertEquals(1, plans.size, "the postings belong to more than one plan")
                            world.transactionRepository.getAllTransactions()
                                .count { it.installmentId == plans.single() }
                        },
                )
                // One purchase, one entry in the log — not twelve. The user made a single decision.
                assertEquals(
                    1,
                    world.activityOf(client).size,
                    "the split left one entry per posting instead of one per act",
                )
            }
        }

    // ------------------------------------------------------------------------------
    // 10.2 — the edit, and what it cannot express
    // ------------------------------------------------------------------------------

    /**
     * **A transfer and a card payment are refused, and the refusal names the reason: more than one
     * monetary leg.**
     *
     * The edit rewrites a posting from a single leg, deleting the old ones first. Both of these have
     * two ends, so rebuilding from one would drop the other with nothing failing — which is why the
     * refusal is the domain's and not a check in the tool.
     */
    @Test
    fun `editing a transfer or a card payment is refused for having more than one monetary leg`() =
        runTest {
            withRegistrationWorld { world, client ->
                val transfer = world.transferId
                val payment = world.paymentId

                listOf(transfer to "transfer", payment to "card payment").forEach { (id, what) ->
                    val response = client.callTool(
                        "update_transaction",
                        """{"id":$id,"amount":99.00}""",
                    )

                    assertTrue(response.isToolError(), "the $what was edited: ${response.toolText()}")
                    assertTrue(
                        "more than one monetary leg" in response.payload().text("reason").orEmpty(),
                        "the refusal of the $what does not name the reason: ${response.toolText()}",
                    )
                }

                // Both legs of each are still there, which is the damage the refusal prevents.
                assertEquals(
                    2,
                    world.transactionRepository.getTransactionById(transfer)!!.monetaryEntries.size,
                )
                assertEquals(
                    2,
                    world.transactionRepository.getTransactionById(payment)!!.monetaryEntries.size,
                )
            }
        }

    /** An ordinary expense, on the other hand, is rewritten — and the ledger holds the new figure. */
    @Test
    fun `editing an expense rewrites it, and what the call did not name survives`() = runTest {
        withRegistrationWorld { world, client ->
            val id = world.groceriesId

            val response = client.callTool("update_transaction", """{"id":$id,"amount":45.50}""")

            assertTrue(!response.isToolError(), "the edit was refused: ${response.toolText()}")

            val stored = world.transactionRepository.getTransactionById(id)!!
            assertEquals(45.50, stored.amount, "the ledger did not take the new amount")
            assertEquals("Mercado", stored.title, "a title nobody named was blanked")
            assertEquals(0L, stored.entries.sumOf { it.amount }, "the rewrite left the posting unbalanced")
        }
    }

    /**
     * **Editing an amount to zero is refused, and the refusal names the removal and the capability
     * that authorises it.**
     *
     * The contortion a withheld removal invites, and worse than the refusal it stands in for: the
     * posting would leave every total and stay in every listing and count. Naming the tool alone
     * would not close it — the tool is exactly what an agent cannot see when the axis is withheld,
     * so the refusal has to name the *capability* too, and where it is granted, or it points at
     * something that is not there.
     */
    @Test
    fun `editing an amount to zero is refused, and names the removal instead`() = runTest {
        withRegistrationWorld { world, client ->
            val id = world.groceriesId

            val response = client.callTool("update_transaction", """{"id":$id,"amount":0}""")

            assertTrue(response.isToolError(), "a zero-value posting was written")
            assertEquals(
                "delete_transaction",
                response.payload().text("try_instead"),
                "the refusal does not name what the agent was reaching for: ${response.toolText()}",
            )

            val reason = assertNotNull(response.payload().text("reason"))
            assertTrue(
                McpToolName.DELETE_TRANSACTION.axis.capability in reason,
                "the refusal does not name the capability removing is on, so an agent that " +
                    "cannot see the tool learns nothing it can act on: $reason",
            )
            assertTrue(
                McpPermissionNotice.WHERE_TO_GRANT in reason,
                "the refusal does not say where that capability is granted: $reason",
            )

            assertEquals(
                300.00,
                world.transactionRepository.getTransactionById(id)!!.amount,
                "the posting was changed anyway",
            )
        }
    }

    // ------------------------------------------------------------------------------
    // 10.3–10.6 — what each creation writes, and what it answers with
    // ------------------------------------------------------------------------------

    /** Every creation answers the thing it made, identity included, and the log reaches it. */
    @Test
    fun `a creation answers what it created, and the log points at it`() = runTest {
        withRegistrationWorld { world, client ->
            val account = client.callTool(
                "create_account",
                """{"name":"Corretora","currency":"USD"}""",
            ).payload().at("account")

            assertEquals("Corretora", account.text("name"))
            assertEquals("USD", account.text("currency"))
            assertTrue(account.identity() > 0, "the account came back without an identity")

            val category = client.callTool(
                "create_category",
                """{"name":"Transporte","type":"expense"}""",
            ).payload().at("category")

            assertEquals("Transporte", category.text("name"))
            assertEquals("expense", category.text("type"))

            val card = client.callTool(
                "create_card",
                """{"name":"Cartão Novo","limit":3000,"closing_day":10,"due_day":20,"currency":"BRL"}""",
            ).payload().at("card")

            assertEquals("Cartão Novo", card.text("name"))
            assertEquals(3000.0, card.at("limit").amount())

            val budget = client.callTool(
                "create_budget",
                """{"title":"Mercado mensal","category_ids":[1],"currency":"BRL","amount":800}""",
            ).payload().at("budget")

            assertEquals("Mercado mensal", budget.text("title"))
            assertEquals(800.0, budget.at("limit").amount())

            val recurring = client.callTool(
                "create_recurring",
                """{"type":"income","amount":5000,"day_of_month":5,"title":"Salário","account_id":1}""",
            ).payload().at("recurring")

            assertEquals("Salário", recurring.text("title"))
            assertTrue(recurring.identity() > 0)

            val log = world.activityOf(client)

            assertEquals(5, log.size, "one creation did not leave a trace: $log")
            assertTrue(
                log.all { it.outcome == AgentActivity.Outcome.APPLIED },
                "a creation that went through was recorded as refused: $log",
            )
            assertTrue(
                log.all { it.reference != null },
                "an entry cannot reach what the act created: $log",
            )
            assertEquals(
                setOf(
                    AgentActivity.Reference.Kind.ACCOUNT,
                    AgentActivity.Reference.Kind.CATEGORY,
                    AgentActivity.Reference.Kind.CREDIT_CARD,
                    AgentActivity.Reference.Kind.BUDGET,
                    AgentActivity.Reference.Kind.RECURRING,
                ),
                log.mapNotNull { it.reference?.kind }.toSet(),
            )
        }
    }

    /** An edit lands on what is stored, and what the call did not name keeps its value. */
    @Test
    fun `an edit changes what it names and nothing else`() = runTest {
        withRegistrationWorld { world, client ->
            client.callTool("update_account", """{"id":1,"name":"Nubank Conta"}""")
            assertEquals("Nubank Conta", world.accountRepository.getAccountById(1)!!.name)
            assertEquals("BRL", world.accountRepository.getAccountById(1)!!.currency)

            client.callTool("update_category", """{"id":1,"name":"Supermercado"}""")
            assertEquals("Supermercado", world.categoryRepository.getCategoryById(1)!!.name)

            val card = world.cards.single()
            client.callTool("update_card", """{"id":${card.id},"limit":9000}""")
            val edited = world.creditCardRepository.getCreditCardById(card.id)!!
            assertEquals(9000.0, edited.limit)
            assertEquals(card.name, edited.name, "a name nobody named was changed")
            assertEquals(card.closingDay, edited.closingDay)
        }
    }

    /**
     * **`create_invoice` declares a cycle, and `delete_invoice` only removes one that never lived.**
     */
    @Test
    fun `an invoice can be declared ahead, and only a future one can be removed`() = runTest {
        withRegistrationWorld { world, client ->
            val card = world.cards.single()

            val declared = client.callTool(
                "create_invoice",
                """{"card_id":${card.id},"due_month":"2026-08"}""",
            )

            assertTrue(!declared.isToolError(), "the cycle was refused: ${declared.toolText()}")
            assertEquals("future", declared.payload().at("invoice").text("status"))

            val futureId = declared.payload().at("invoice").identity()
            val removed = client.callTool("delete_invoice", """{"id":$futureId}""")

            assertTrue(!removed.isToolError(), "a future cycle was not removable: ${removed.toolText()}")
            assertEquals(null, world.invoiceRepository.getInvoiceById(futureId))

            // The open one is where spending is landing right now, and it stays.
            val openId = world.invoices.single { it.status.isOpen }.id
            val refused = client.callTool("delete_invoice", """{"id":$openId}""")

            assertTrue(refused.isToolError(), "the open cycle was removed")
            assertTrue(
                "future or retroactive" in refused.payload().text("reason").orEmpty(),
                "the refusal does not say why: ${refused.toolText()}",
            )
            assertNotNull(world.invoiceRepository.getInvoiceById(openId))
        }
    }

    // ------------------------------------------------------------------------------
    // 10.7 — the removals
    // ------------------------------------------------------------------------------

    /** Every `delete_*` removes what it names, and says what went with it. */
    @Test
    fun `each removal takes what it names, and the log records the act`() = runTest {
        withRegistrationWorld { world, client ->
            // Something with no dependent at all, created for the purpose.
            val budgetId = client.callTool(
                "create_budget",
                """{"title":"Descartável","category_ids":[],"currency":"BRL","amount":10}""",
            ).payload().at("budget").identity()

            val accountId = client.callTool(
                "create_account",
                """{"name":"Conta vazia","currency":"BRL"}""",
            ).payload().at("account").identity()

            val categoryId = client.callTool(
                "create_category",
                """{"name":"Sem uso","type":"expense"}""",
            ).payload().at("category").identity()

            val recurringId = client.callTool(
                "create_recurring",
                """{"type":"expense","amount":30,"day_of_month":9,"title":"Streaming","account_id":1}""",
            ).payload().at("recurring").identity()

            listOf(
                "delete_budget" to budgetId,
                "delete_account" to accountId,
                "delete_category" to categoryId,
                "delete_recurring" to recurringId,
            ).forEach { (tool, id) ->
                val response = client.callTool(tool, """{"id":$id}""")
                assertTrue(!response.isToolError(), "`$tool` refused: ${response.toolText()}")
                assertEquals(id, response.payload().identity())
            }

            assertEquals(null, world.accountRepository.getAccountById(accountId))
            assertEquals(null, world.categoryRepository.getCategoryById(categoryId))
            assertEquals(null, world.budgetRepository.getBudgetById(budgetId))
            assertEquals(null, world.recurringRepository.getRecurringById(recurringId))

            // A posting, and the plan a purchase was split into.
            val postingId = world.groceriesId
            val removedPosting = client.callTool("delete_transaction", """{"id":$postingId}""")
            assertTrue(!removedPosting.isToolError(), removedPosting.toolText())
            assertEquals(null, world.transactionRepository.getTransactionById(postingId))
        }
    }

    /** Removing a plan takes every share with it — one decision, one unit of work. */
    @Test
    fun `removing an instalment plan takes every posting of it`() = runTest {
        withRegistrationWorld { world, client ->
            val card = world.cards.single()

            val planId = client.callTool(
                "create_installment",
                """{"card_id":${card.id},"amount":600,"count":3,"date":"2026-03-10","title":"Fone"}""",
            ).payload().at("installment").identity()

            assertEquals(
                3,
                world.transactionRepository.getAllTransactions().count { it.installmentId == planId },
            )

            val removed = client.callTool("delete_installment", """{"id":$planId}""")

            assertTrue(!removed.isToolError(), removed.toolText())
            assertEquals(
                0,
                world.transactionRepository.getAllTransactions().count { it.installmentId == planId },
                "the plan went and its postings stayed",
            )
            assertEquals(null, world.installmentRepository.getInstallmentById(planId))
        }
    }

    /** Correcting a plan's own description moves no money. */
    @Test
    fun `correcting an instalment plan leaves the ledger untouched`() = runTest {
        withRegistrationWorld { world, client ->
            val card = world.cards.single()
            val planId = client.callTool(
                "create_installment",
                """{"card_id":${card.id},"amount":600,"count":3,"date":"2026-03-10","title":"Fone"}""",
            ).payload().at("installment").identity()

            val before = world.database.entryDao().getAll()

            val corrected = client.callTool(
                "update_installment",
                """{"id":$planId,"count":4,"total_amount":800}""",
            )

            assertTrue(!corrected.isToolError(), corrected.toolText())
            assertEquals(4, world.installmentRepository.getInstallmentById(planId)!!.count)
            assertEquals(800.0, world.installmentRepository.getInstallmentById(planId)!!.totalAmount)
            assertEquals(
                before,
                world.database.entryDao().getAll(),
                "correcting the plan's description moved money",
            )
        }
    }

    // ------------------------------------------------------------------------------
    // 10.8 — a removal the domain refuses, and the alternative it names
    // ------------------------------------------------------------------------------

    /**
     * **Removing a category with postings is refused, and the refusal names archiving.**
     *
     * Both halves matter. The refusal alone teaches the agent to invent a way round it — the
     * simulation of D13 saw exactly that — and the alternative is not this tool's to choose:
     * `retireActionOf` owns archive-versus-delete, and it is the same owner the screens ask.
     */
    @Test
    fun `removing a category with postings is refused, and the refusal names archiving`() = runTest {
        withRegistrationWorld { world, client ->
            val response = client.callTool("delete_category", """{"id":1}""")

            assertTrue(response.isToolError(), "a category with postings was removed")

            val refusal = response.payload()
            assertEquals(
                "Cannot delete a category that has transactions",
                refusal.text("reason"),
                "the refusal is not the domain's own words",
            )
            assertEquals(
                "archive_entity",
                refusal.text("try_instead"),
                "the refusal does not name what the domain allows in its place",
            )
            assertNotNull(world.categoryRepository.getCategoryById(1), "the category went anyway")

            val log = world.activityOf(client)
            assertEquals(1, log.size, "the refusal left no trace: $log")
            assertEquals(AgentActivity.Outcome.REFUSED, log.single().outcome)
            assertEquals(
                "Cannot delete a category that has transactions",
                log.single().detail,
                "the log does not say why the agent could not do it",
            )
        }
    }

    /** The same shape for an account and a card that moved: refused, and archiving named. */
    @Test
    fun `removing an account or a card that moved is refused, and archiving is named`() = runTest {
        withRegistrationWorld { world, client ->
            val card = world.cards.single()

            listOf("delete_account" to 2L, "delete_card" to card.id).forEach { (tool, id) ->
                val response = client.callTool(tool, """{"id":$id}""")

                assertTrue(response.isToolError(), "`$tool` removed something that had moved")
                assertEquals(
                    "archive_entity",
                    response.payload().text("try_instead"),
                    "`$tool` refused without naming the alternative: ${response.toolText()}",
                )
            }
        }
    }

    /** An identity that matches nothing says which one, before anything is attempted. */
    @Test
    fun `an identity that matches nothing is refused by name`() = runTest {
        withRegistrationWorld { _, client ->
            val response = client.callTool("delete_transaction", """{"id":9999}""")

            assertTrue(response.isToolError())
            assertEquals(
                "No transaction with id 9999 exists.",
                response.payload().text("reason"),
            )
        }
    }

    // ------------------------------------------------------------------------------
    // The surroundings
    // ------------------------------------------------------------------------------

    /**
     * **Every tool of the family leaves exactly one entry in the activity log — including when it is
     * refused.**
     *
     * The log is the only place the *authorship* of a write appears: reactivity delivers the result
     * and says nothing about where it came from, so a tool that forgot to be recorded would change
     * the ledger invisibly. Swept mechanically rather than asserted per tool, because the failure
     * mode is a tool added later that is never recorded, and no payload of that tool would look
     * wrong.
     *
     * Refusals are swept along with the writes on purpose: a refused attempt is exactly what
     * explains to the user why the agent said it could not do something, and it is the outcome most
     * of these calls produce here — every identity below matches nothing.
     */
    @Test
    fun `every tool of the family leaves one entry in the log, applied or refused`() = runTest {
        withRegistrationWorld { world, client ->
            val family = McpSurface.offered.filter {
                it.axis == McpPermissionAxis.RECORD || it.axis == McpPermissionAxis.REMOVE
            }

            assertEquals(23, family.size, "the family is not the size it was decided to be")

            family.forEach { tool ->
                client.callTool(tool.wireName, MISSING_IDENTITY.getValue(tool))
            }

            val log = world.activityOf(client)

            assertEquals(
                family.map { it.wireName }.sorted(),
                log.map { it.operation }.sorted(),
                "a tool of the registration family changed something and left no trace",
            )
            assertTrue(
                log.all { it.summary.isNotBlank() },
                "an entry says nothing about what the act was about: $log",
            )
            assertTrue(
                log.all { it.outcome == AgentActivity.Outcome.REFUSED && it.detail != null },
                "an entry does not say why the attempt was refused: $log",
            )
        }
    }

    /**
     * A world with March in it, a real server over a real socket, and the activity log the writes
     * land in — the same harness the desktop assembles.
     */
    private suspend fun withRegistrationWorld(
        block: suspend (RegistrationWorld, McpConversation) -> Unit,
    ) {
        AgentWorld().use { world ->
            val seeded = world.seedRegistration()

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

        /**
         * The smallest call each tool of the family accepts, aimed at an identity that matches
         * nothing.
         *
         * Every one of them is refused, which is the point: what is being swept is that the attempt
         * is *recorded*, and a refusal is the outcome the log exists to explain. The arguments still
         * satisfy each schema, so the call reaches the tool rather than being turned away before it.
         */
        val MISSING_IDENTITY: Map<McpToolName, String> = mapOf(
            McpToolName.CREATE_TRANSACTION to """{"type":"expense","amount":10,"account_id":9999}""",
            McpToolName.UPDATE_TRANSACTION to """{"id":9999}""",
            McpToolName.DELETE_TRANSACTION to """{"id":9999}""",
            McpToolName.CREATE_ACCOUNT to """{"name":"Nubank","currency":"BRL"}""",
            McpToolName.UPDATE_ACCOUNT to """{"id":9999}""",
            McpToolName.DELETE_ACCOUNT to """{"id":9999}""",
            McpToolName.CREATE_CARD to
                """{"name":"Cartão","limit":1,"closing_day":1,"due_day":2,"currency":"BRL"}""",
            McpToolName.UPDATE_CARD to """{"id":9999}""",
            McpToolName.DELETE_CARD to """{"id":9999}""",
            McpToolName.CREATE_CATEGORY to """{"name":"Mercado","type":"expense"}""",
            McpToolName.UPDATE_CATEGORY to """{"id":9999,"name":"Outro"}""",
            McpToolName.DELETE_CATEGORY to """{"id":9999}""",
            McpToolName.CREATE_BUDGET to
                """{"title":"B","category_ids":[9999],"currency":"BRL","amount":1}""",
            McpToolName.UPDATE_BUDGET to """{"id":9999}""",
            McpToolName.DELETE_BUDGET to """{"id":9999}""",
            McpToolName.CREATE_RECURRING to
                """{"type":"expense","amount":1,"day_of_month":1,"account_id":9999}""",
            McpToolName.UPDATE_RECURRING to """{"id":9999}""",
            McpToolName.DELETE_RECURRING to """{"id":9999}""",
            McpToolName.CREATE_INSTALLMENT to """{"card_id":9999,"amount":10,"count":2}""",
            McpToolName.UPDATE_INSTALLMENT to """{"id":9999}""",
            McpToolName.DELETE_INSTALLMENT to """{"id":9999}""",
            McpToolName.CREATE_INVOICE to """{"card_id":9999,"due_month":"2026-09"}""",
            McpToolName.DELETE_INVOICE to """{"id":9999}""",
        )
    }
}
