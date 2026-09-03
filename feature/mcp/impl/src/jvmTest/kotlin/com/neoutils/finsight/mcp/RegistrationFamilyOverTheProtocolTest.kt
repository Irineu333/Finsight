package com.neoutils.finsight.mcp

import com.neoutils.finsight.domain.model.TransactionType
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
import kotlin.test.assertNull
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

    /**
     * **Splitting a purchase and marking it recurring are refused together, rather than one of the
     * two being dropped.**
     *
     * `RegisterTransactionUseCase` returns down the instalment branch before `isRecurring` is read,
     * so the pair writes the split and opens no template. The sheet reaches that same outcome on
     * purpose — and may, because it stops showing the mark it drops. Nothing was ever shown here, so
     * the drop would answer "Recorded as 3 instalments" to a caller who also asked for a template,
     * and the agent would tell the user a subscription repeats every month. It does not.
     */
    @Test
    fun `instalments and a recurring mark are refused together, not silently reconciled`() = runTest {
        withRegistrationWorld { world, client ->
            val card = world.cards.single()

            val response = client.callTool(
                "create_transaction",
                """
                {"type":"expense","amount":300.00,"title":"Subscription","card_id":${card.id},
                 "installments":3,"is_recurring":true}
                """.trimIndent().replace("\n", ""),
            )

            assertTrue(response.isToolError(), "the pair was accepted, and half of it dropped")

            val reason = assertNotNull(response.payload().text("reason"))
            assertTrue(
                "installments" in reason && "is_recurring" in reason,
                "the refusal does not name both arguments, so the agent cannot tell which to drop: $reason",
            )

            assertEquals(
                0,
                world.transactionRepository.getAllTransactions().count { it.title == "Subscription" },
                "the split was written anyway",
            )
            assertTrue(
                world.recurringRepository.observeAllRecurring().first().isEmpty(),
                "a template was opened anyway",
            )
        }
    }

    // ------------------------------------------------------------------------------
    // What the form would normalise away, refused instead
    // ------------------------------------------------------------------------------

    /**
     * **A category the direction cannot accept is refused, and the refusal names the category, its
     * kind and the direction.**
     *
     * `TransactionForm.from` keeps a category only while it accepts the type, which is right for the
     * sheet: switching to income re-lists the selector, so the drop takes nothing the user chose.
     * The category here was *declared*, and dropping it writes an income with no classification and
     * answers "Recorded." — the agent then reports the classification it asked for, and the user
     * finds the posting under "no category" with nothing to explain why.
     */
    @Test
    fun `a category the direction does not accept is refused, naming it and both kinds`() = runTest {
        withRegistrationWorld { world, client ->
            val before = world.database.entryDao().getAll()

            val response = client.callTool(
                "create_transaction",
                """{"type":"income","amount":1200.00,"title":"Salário","account_id":1,"category_id":1}""",
            )

            assertTrue(response.isToolError(), "an income was classified under an expense category")

            val reason = assertNotNull(response.payload().text("reason"))
            listOf("category_id", "Mercado", "expense", "income").forEach {
                assertTrue(
                    it in reason,
                    "the refusal does not name `$it`, so the agent cannot tell which end is wrong: $reason",
                )
            }

            assertEquals(
                0,
                world.transactionRepository.getAllTransactions().count { it.title == "Salário" },
                "the income was written, without the classification the call asked for",
            )
            assertEquals(before, world.database.entryDao().getAll(), "the ledger moved anyway")
        }
    }

    /**
     * **A split asked for on an account is refused, rather than flattened to one posting.**
     *
     * Instalments are a card affordance — the shares land on the following invoices — so the form
     * forces the count back to one whenever the target is an account. Silently, here, that is a
     * purchase the caller asked to spread over three invoices written once for the whole amount,
     * and answered as recorded.
     */
    @Test
    fun `a split on an account is refused, naming the instalments and the account`() = runTest {
        withRegistrationWorld { world, client ->
            val before = world.database.entryDao().getAll()

            val response = client.callTool(
                "create_transaction",
                """{"type":"expense","amount":600.00,"title":"Fone","account_id":1,"installments":3}""",
            )

            assertTrue(response.isToolError(), "the split was flattened onto an account")

            val reason = assertNotNull(response.payload().text("reason"))
            assertTrue(
                "installments" in reason && "account_id" in reason,
                "the refusal does not name both arguments, so the agent cannot tell which to change: $reason",
            )

            assertEquals(
                0,
                world.transactionRepository.getAllTransactions().count { it.title == "Fone" },
                "the purchase was written as a single posting for the whole amount",
            )
            assertEquals(before, world.database.entryDao().getAll(), "the ledger moved anyway")
        }
    }

    /**
     * **An `invoice_month` aimed at an account is refused, rather than carried to a build that has
     * no invoice to put it on.**
     *
     * The sibling of the split above, and the one drop on this surface the form does not make:
     * `TransactionForm.from` carries `invoiceDueMonth` through untouched, and the account branch of
     * `BuildTransactionUseCaseImpl` returns without ever reading it. Silently, that is a purchase
     * the caller placed on a named invoice, written onto no invoice at all, and answered as
     * recorded.
     */
    @Test
    fun `an invoice_month on an account is refused, naming the month and the account`() = runTest {
        withRegistrationWorld { world, client ->
            val before = world.database.entryDao().getAll()

            val response = client.callTool(
                "create_transaction",
                """{"type":"expense","amount":90.00,"title":"Padaria","account_id":1,"invoice_month":"2026-04"}""",
            )

            assertTrue(response.isToolError(), "the invoice month was dropped and the posting written")

            val reason = assertNotNull(response.payload().text("reason"))
            assertTrue(
                "invoice_month" in reason && "account_id" in reason,
                "the refusal does not name both arguments, so the agent cannot tell which to change: $reason",
            )

            assertEquals(
                0,
                world.transactionRepository.getAllTransactions().count { it.title == "Padaria" },
                "the posting was written without the invoice the call named",
            )
            assertEquals(before, world.database.entryDao().getAll(), "the ledger moved anyway")
        }
    }

    /**
     * **An income aimed at a card is refused for the card, not for an account nobody named.**
     *
     * This pair was never written silently: the form drops the card, leaves the target on an account
     * with no account in it, and what comes back asks for the account. The refusal is the damage —
     * it points at `account_id`, which the call never gave and which is not what the caller got
     * wrong, and an agent acting on it invents an account for an income that belongs nowhere near
     * one. A card takes expenses only, and that is what the refusal has to say.
     */
    @Test
    fun `an income on a card is refused for the card, not for a missing account`() = runTest {
        withRegistrationWorld { world, client ->
            val card = world.cards.single()
            val before = world.database.entryDao().getAll()

            val response = client.callTool(
                "create_transaction",
                """{"type":"income","amount":500.00,"title":"Estorno","card_id":${card.id}}""",
            )

            assertTrue(response.isToolError(), "an income was recorded on a card")

            val reason = assertNotNull(response.payload().text("reason"))
            assertTrue(
                "card_id" in reason && "account_id" in reason,
                "the refusal does not name the arguments the caller has to change: $reason",
            )
            assertTrue(
                "expenses only" in reason,
                "the refusal does not say what the card cannot take, so it still reads as a " +
                    "missing account: $reason",
            )

            assertEquals(
                0,
                world.transactionRepository.getAllTransactions().count { it.title == "Estorno" },
                "the income was written anyway",
            )
            assertEquals(before, world.database.entryDao().getAll(), "the ledger moved anyway")
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

    /**
     * **The stored category is not the caller's to lose in silence.**
     *
     * `TransactionForm.from` drops a category whose type the new direction does not accept, and
     * that is right for the sheet: flipping the direction there re-offers the selector, so the
     * drop takes nothing the user still believes is set. Nothing is offered here, and the
     * classification is carried rather than named, so an edit naming only `type` is refused: the
     * alternative is taking something the call never mentioned, under an answer saying that
     * everything it did not name kept its value.
     */
    @Test
    fun `flipping the direction is refused rather than dropping the stored category`() = runTest {
        withRegistrationWorld { world, client ->
            val id = world.groceriesId
            val before = world.transactionRepository.getTransactionById(id)!!.nominalDimensionId

            val response = client.callTool("update_transaction", """{"id":$id,"type":"income"}""")

            assertTrue(
                response.isToolError(),
                "the stored category was dropped and the edit answered as complete: " +
                    response.toolText(),
            )

            val reason = assertNotNull(response.payload().text("reason"))
            assertTrue(
                "Mercado" in reason && "category_id" in reason,
                "the refusal names neither the classification at stake nor how to settle it, so " +
                    "the agent cannot tell what it was about to lose: $reason",
            )

            assertEquals(
                before,
                world.transactionRepository.getTransactionById(id)!!.nominalDimensionId,
                "the classification was lost anyway",
            )
        }
    }

    /**
     * **A category declared beside a direction it cannot classify is refused, naming both.**
     *
     * The mirror of what the creation already refuses. Dropped silently, the answer reports a
     * classification that never reached the ledger, and the agent relays it to the user.
     */
    @Test
    fun `a declared category the direction cannot accept is refused, naming both`() = runTest {
        withRegistrationWorld { world, client ->
            val id = world.groceriesId
            val category = world.categoryRepository.getAllCategories().single()

            val response = client.callTool(
                "update_transaction",
                """{"id":$id,"type":"income","category_id":${category.id}}""",
            )

            assertTrue(
                response.isToolError(),
                "the declared category was dropped and the edit answered as complete: " +
                    response.toolText(),
            )

            val reason = assertNotNull(response.payload().text("reason"))
            assertTrue(
                "category_id" in reason && "income" in reason,
                "the refusal does not name the argument and the direction that disagree: $reason",
            )
        }
    }

    /**
     * **An income moved onto a card is refused for the card, not for an account nobody named.**
     *
     * A card takes expenses only, and that is what the refusal has to say. Left to the form, the
     * card is dropped and the target falls back to an account with none in it, so what comes back
     * asks for `account_id` — an argument the call never gave, and one an agent answers by
     * inventing an account for a posting that belongs nowhere near one.
     */
    @Test
    fun `moving an income onto a card is refused for the card, not for a missing account`() =
        runTest {
            withRegistrationWorld { world, client ->
                val id = world.groceriesId
                val card = world.cards.single()

                val response = client.callTool(
                    "update_transaction",
                    """{"id":$id,"type":"income","card_id":${card.id}}""",
                )

                assertTrue(response.isToolError(), "an income was moved onto a card")

                val reason = assertNotNull(response.payload().text("reason"))
                assertTrue(
                    "card_id" in reason && "expenses only" in reason,
                    "the refusal points at an argument the call never gave, so it reads as a " +
                        "missing account: $reason",
                )
            }
        }

    /**
     * **An `invoice_month` given for a posting that does not sit on a card is refused, not
     * swallowed.**
     *
     * The other drops on this surface happen in `TransactionForm.from`, and the refusals above
     * stand in front of them. This one happens a step later: `BuildTransactionUseCaseImpl` returns
     * from its account branch without ever reading `form.invoiceDueMonth`, so the month reaches
     * nothing at all. What comes back is the answer written for the opposite case — everything the
     * call did not name kept its value — about the one thing the call did name.
     */
    @Test
    fun `an invoice_month on a posting that sits in an account is refused, not dropped`() = runTest {
        withRegistrationWorld { world, client ->
            val id = world.groceriesId

            val response = client.callTool(
                "update_transaction",
                """{"id":$id,"invoice_month":"2026-04"}""",
            )

            assertTrue(
                response.isToolError(),
                "the invoice month went nowhere and the edit answered as complete: " +
                    response.toolText(),
            )

            val reason = assertNotNull(response.payload().text("reason"))
            assertTrue(
                "invoice_month" in reason && "card_id" in reason,
                "the refusal names neither the argument that cannot apply nor what would make it " +
                    "apply, so the agent cannot tell what happened to the month it gave: $reason",
            )
        }
    }

    /**
     * **The same refusal when the account is where the call is moving the posting to.**
     *
     * The two arguments are consistent with each other only if read one at a time: a card posting
     * does have invoices to move between, and this call takes that away in the same breath as it
     * names one. The target the edit resolves to is what decides, not the one the posting had.
     */
    @Test
    fun `an invoice_month named while the posting moves off the card is refused`() = runTest {
        withRegistrationWorld { world, client ->
            val card = world.cards.single()
            val account = assertNotNull(
                world.transactionRepository.getTransactionById(world.groceriesId)?.sourceAccount,
                "the seeded expense sits in no account",
            )

            val purchase = client.callTool(
                "create_transaction",
                """{"type":"expense","amount":80.00,"title":"Livraria","card_id":${card.id},"date":"2026-03-10"}""",
            )
            assertTrue(
                !purchase.isToolError(),
                "the card purchase under test was refused: ${purchase.toolText()}",
            )
            val id = purchase.payload().at("transaction").identity()

            val response = client.callTool(
                "update_transaction",
                """{"id":$id,"account_id":${account.id},"invoice_month":"2026-04"}""",
            )

            assertTrue(
                response.isToolError(),
                "the posting left the card, the invoice month went nowhere, and the edit " +
                    "answered as complete: ${response.toolText()}",
            )

            val reason = assertNotNull(response.payload().text("reason"))
            assertTrue(
                "invoice_month" in reason && "account_id" in reason,
                "the refusal does not name the two arguments that disagree: $reason",
            )
        }
    }

    /**
     * **Erasing a classification is something the call can say, which is what makes the two
     * refusals above a redirection rather than a dead end.**
     *
     * On an edit, absence means "keep what it has" — so leaving `category_id` out is the one thing
     * that cannot also mean "none", and an explicit JSON `null` is read as absence everywhere on
     * this surface (`ToolSupport.argument`). Zero is how the call names it, as `confirm_recurring`
     * already spells it. Without it, a classified posting could never change direction here at
     * all — something the app's own sheet allows.
     */
    @Test
    fun `a category_id of zero changes the direction by leaving the posting unclassified`() =
        runTest {
            withRegistrationWorld { world, client ->
                val id = world.groceriesId
                assertNotNull(
                    world.transactionRepository.getTransactionById(id)!!.nominalDimensionId,
                    "the posting under test was never classified",
                )

                val response = client.callTool(
                    "update_transaction",
                    """{"id":$id,"type":"income","category_id":0}""",
                )

                assertTrue(
                    !response.isToolError(),
                    "the refusals send the caller here, and here refuses too: ${response.toolText()}",
                )
                assertNull(
                    world.transactionRepository.getTransactionById(id)!!.nominalDimensionId,
                    "the classification the call gave up is still on the posting",
                )
            }
        }

    /** The same zero on its own: the classification goes, the direction stays. */
    @Test
    fun `a category_id of zero clears the classification without touching anything else`() =
        runTest {
            withRegistrationWorld { world, client ->
                val id = world.groceriesId

                val response = client.callTool(
                    "update_transaction",
                    """{"id":$id,"category_id":0}""",
                )

                assertTrue(!response.isToolError(), "clearing was refused: ${response.toolText()}")

                val edited = world.transactionRepository.getTransactionById(id)!!
                assertNull(edited.nominalDimensionId, "the classification is still on the posting")
                assertEquals("Mercado", edited.title, "the title moved with the classification")
                assertEquals(300.00, edited.amount, "the amount moved with the classification")
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
    // 10.6b — the same rule, on the two tools that were left holding it
    // ------------------------------------------------------------------------------

    /**
     * **A split classified under a category that cannot classify it is refused, not written
     * unclassified.**
     *
     * An instalment plan is always an expense, so the category that does not fit is an income one.
     * Dropped, the three postings land with no classification at all under an answer that says
     * they were recorded — the same shape the creation and the edit already refuse.
     */
    @Test
    fun `an income category on a split is refused, naming the category and the direction`() =
        runTest {
            withRegistrationWorld { world, client ->
                val card = world.cards.single()
                val salary = client.callTool(
                    "create_category",
                    """{"name":"Salário","type":"income"}""",
                ).payload().at("category").identity()

                val response = client.callTool(
                    "create_installment",
                    """{"card_id":${card.id},"amount":900,"count":3,"date":"2026-03-10",
                     "title":"Compra","category_id":$salary}""".trimIndent().replace("\n", ""),
                )

                assertTrue(
                    response.isToolError(),
                    "the classification was dropped and the split answered as recorded: " +
                        response.toolText(),
                )

                val reason = assertNotNull(response.payload().text("reason"))
                assertTrue(
                    "category_id" in reason && "expense" in reason,
                    "the refusal does not name the argument and the direction that disagree: $reason",
                )

                assertEquals(
                    0,
                    world.transactionRepository.getAllTransactions().count { it.title == "Compra" },
                    "the split was written anyway",
                )
            }
        }

    /**
     * **An income template aimed at a card is refused for the card.**
     *
     * A card takes expenses only, and the tool says so before the form is built. Left to
     * `RecurringForm.toRecurring`, the card is nulled for an income and an account asked for
     * instead, so what comes back names `account_id` — an argument the call never gave, and the
     * defect the creation of a posting already shed.
     */
    @Test
    fun `an income template on a card is refused for the card, not for a missing account`() =
        runTest {
            withRegistrationWorld { world, client ->
                val card = world.cards.single()

                val response = client.callTool(
                    "create_recurring",
                    """{"type":"income","amount":5000,"day_of_month":5,"title":"Estorno","card_id":${card.id}}""",
                )

                assertTrue(response.isToolError(), "an income template was opened on a card")

                val reason = assertNotNull(response.payload().text("reason"))
                assertTrue(
                    "card_id" in reason && "expenses only" in reason,
                    "the refusal points at an argument the call never gave: $reason",
                )
            }
        }

    /**
     * **A template does not keep a classification its direction cannot carry.**
     *
     * The refusal stands in for something worse than a drop: unfiltered, a template is *persisted*
     * as an income classified under an expense category — a shape the domain does not model — under
     * an answer saying the edit landed. `toRecurring` settles what the direction can carry, and the
     * tool refuses ahead of it so the classification is not taken from a call that never named it.
     */
    @Test
    fun `flipping a template's direction is refused rather than keeping the stored category`() =
        runTest {
            withRegistrationWorld { world, client ->
                val id = client.callTool(
                    "create_recurring",
                    """{"type":"expense","amount":300,"day_of_month":5,"category_id":1,"account_id":1}""",
                ).payload().at("recurring").identity()

                val response = client.callTool(
                    "update_recurring",
                    """{"id":$id,"type":"income"}""",
                )

                assertTrue(
                    response.isToolError(),
                    "an income template was stored under an expense category: ${response.toolText()}",
                )

                val reason = assertNotNull(response.payload().text("reason"))
                assertTrue(
                    "Mercado" in reason && "category_id" in reason,
                    "the refusal names neither the classification at stake nor how to settle it: $reason",
                )

                val stored = assertNotNull(world.recurringRepository.getRecurringById(id))
                assertEquals(
                    TransactionType.EXPENSE,
                    stored.type,
                    "the template changed direction anyway",
                )
            }
        }

    /** The declared half of the same rule, as the creation of a posting already refuses it. */
    @Test
    fun `a declared category a template's direction cannot carry is refused, naming both`() =
        runTest {
            withRegistrationWorld { _, client ->
                val response = client.callTool(
                    "create_recurring",
                    """{"type":"income","amount":5000,"day_of_month":5,"category_id":1,"account_id":1}""",
                )

                assertTrue(
                    response.isToolError(),
                    "the classification was dropped and the template answered as opened: " +
                        response.toolText(),
                )

                val reason = assertNotNull(response.payload().text("reason"))
                assertTrue(
                    "category_id" in reason && "income" in reason,
                    "the refusal does not name the argument and the direction that disagree: $reason",
                )
            }
        }

    /**
     * And the way out the refusal above names: on this edit too, absence means *keep what it has*,
     * so zero is what says the template has none.
     */
    @Test
    fun `a category_id of zero lets a template change direction unclassified`() = runTest {
        withRegistrationWorld { world, client ->
            val id = client.callTool(
                "create_recurring",
                """{"type":"expense","amount":300,"day_of_month":5,"title":"Aluguel",
                 "category_id":1,"account_id":1}""".trimIndent().replace("\n", ""),
            ).payload().at("recurring").identity()

            val response = client.callTool(
                "update_recurring",
                """{"id":$id,"type":"income","category_id":0}""",
            )

            assertTrue(
                !response.isToolError(),
                "the refusal sends the caller here, and here refuses too: ${response.toolText()}",
            )

            val stored = assertNotNull(world.recurringRepository.getRecurringById(id))
            assertEquals(TransactionType.INCOME, stored.type)
            assertNull(stored.category, "the classification the call gave up is still on the template")
            assertEquals("Aluguel", stored.title, "the title moved with the classification")
        }
    }

    /**
     * **A template goes by its title or, having none, by its category — so the one that has only a
     * category cannot simply give it up.**
     *
     * `TITLE_OR_CATEGORY_REQUIRED` is the domain's rule and the right answer: a template with
     * neither is nameless. The refusal costs the agent one more round trip, and the alternative —
     * promoting the category's name to a title nobody typed — is a rename the call never asked for,
     * reported as an edit that changed only what it named.
     */
    @Test
    fun `clearing the only name a template has is refused, not settled by renaming it`() = runTest {
        withRegistrationWorld { world, client ->
            val id = client.callTool(
                "create_recurring",
                """{"type":"expense","amount":300,"day_of_month":5,"category_id":1,"account_id":1}""",
            ).payload().at("recurring").identity()

            val response = client.callTool(
                "update_recurring",
                """{"id":$id,"category_id":0}""",
            )

            assertTrue(
                response.isToolError(),
                "the template was left with no name, or given one nobody typed: ${response.toolText()}",
            )

            val reason = assertNotNull(response.payload().text("reason"))
            assertTrue(
                "itle" in reason && "categor" in reason,
                "the refusal is not the one about the name, so this test would pass on any other: $reason",
            )

            val stored = assertNotNull(world.recurringRepository.getRecurringById(id))
            assertNull(stored.title, "the template was renamed by an edit that did not name a title")
            assertEquals("Mercado", stored.category?.name, "the classification went anyway")
        }
    }


    /**
     * **A cycle posts in one place, and the edit says so instead of choosing for the caller.**
     *
     * The creation of a template refuses the pair, and so does the edit of a posting. Without it
     * here the account simply wins, the card is resolved and thrown away, and the answer reports an
     * edit that landed — while the `card_id` went nowhere. It also makes the card refusal beside it
     * conditional: naming an `account_id` too is enough to walk past it.
     */
    @Test
    fun `an edit naming both an account and a card is refused, as the creation already refuses it`() =
        runTest {
            withRegistrationWorld { world, client ->
                val card = world.cards.single()
                val id = client.callTool(
                    "create_recurring",
                    """{"type":"expense","amount":300,"day_of_month":5,"title":"Aluguel","account_id":1}""",
                ).payload().at("recurring").identity()

                val response = client.callTool(
                    "update_recurring",
                    """{"id":$id,"account_id":2,"card_id":${card.id}}""",
                )

                assertTrue(
                    response.isToolError(),
                    "the card was resolved and dropped, and the edit answered as landed: " +
                        response.toolText(),
                )

                val reason = assertNotNull(response.payload().text("reason"))
                assertTrue(
                    "account_id" in reason && "card_id" in reason,
                    "the refusal does not name the two arguments the caller has to choose between: $reason",
                )
            }
        }

    /**
     * **The card refusal is not conditional on how many other arguments the call carries.**
     *
     * With the pair refused ahead of it, `type` income beside a `card_id` is refused for the card
     * whether or not an `account_id` came along — otherwise the way past the rule is to name one
     * more thing.
     */
    @Test
    fun `an income beside a card is refused even when an account is named too`() = runTest {
        withRegistrationWorld { world, client ->
            val card = world.cards.single()
            val id = client.callTool(
                "create_recurring",
                """{"type":"expense","amount":300,"day_of_month":5,"title":"Luz","account_id":1}""",
            ).payload().at("recurring").identity()

            val response = client.callTool(
                "update_recurring",
                """{"id":$id,"type":"income","card_id":${card.id},"account_id":1}""",
            )

            assertTrue(response.isToolError(), "the refusal was walked past: ${response.toolText()}")

            val stored = assertNotNull(world.recurringRepository.getRecurringById(id))
            assertEquals(TransactionType.EXPENSE, stored.type, "the template changed direction anyway")
        }
    }

    /**
     * **The declared half of the rule, on the edit** — the creation's counterpart is above, and
     * this one had no test of its own.
     */
    @Test
    fun `a declared category the edited direction cannot carry is refused, naming both`() = runTest {
        withRegistrationWorld { _, client ->
            val salary = client.callTool(
                "create_category",
                """{"name":"Salário","type":"income"}""",
            ).payload().at("category").identity()

            val id = client.callTool(
                "create_recurring",
                """{"type":"expense","amount":300,"day_of_month":5,"title":"Aluguel","account_id":1}""",
            ).payload().at("recurring").identity()

            val response = client.callTool(
                "update_recurring",
                """{"id":$id,"category_id":$salary}""",
            )

            assertTrue(
                response.isToolError(),
                "the declared category was dropped and the edit answered as complete: " +
                    response.toolText(),
            )

            val reason = assertNotNull(response.payload().text("reason"))
            assertTrue(
                "category_id" in reason && "expense" in reason,
                "the refusal does not name the argument and the direction that disagree: $reason",
            )
        }
    }

    /**
     * **The refusal has to be a rule, not a wall**: a split classified under a category that fits
     * goes through, and every share carries it.
     */
    @Test
    fun `an expense category on a split is kept, on every share`() = runTest {
        withRegistrationWorld { world, client ->
            val card = world.cards.single()

            val response = client.callTool(
                "create_installment",
                """{"card_id":${card.id},"amount":900,"count":3,"date":"2026-03-10",
                 "title":"Compra","category_id":1}""".trimIndent().replace("\n", ""),
            )

            assertTrue(!response.isToolError(), "a fitting category was refused: ${response.toolText()}")

            val written = world.transactionRepository.getAllTransactions().filter { it.title == "Compra" }
            assertEquals(3, written.size, "the split did not produce three postings")
            assertTrue(
                written.all { it.nominalDimensionId == 1L },
                "a share landed unclassified: ${written.map { it.nominalDimensionId }}",
            )
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
