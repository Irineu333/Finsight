package com.neoutils.finsight.mcp

import com.neoutils.finsight.database.entity.AccountEntity
import com.neoutils.finsight.domain.model.Budget
import com.neoutils.finsight.domain.model.Installment
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.model.TransactionType
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **The catalogue family, driven over a real socket** — the ten tools an agent turns a name into an
 * identity with.
 *
 * Every one is called the way a client calls it, against a server bound to loopback with a real
 * ledger behind it. What is asserted is what a listing owes beyond its items: the figure that
 * belongs beside each name, the ledger read the figure came from, and — for the two pairs of tools
 * whose answers overlap — a description that says which cut it offers, so nobody has to call both
 * and compare.
 */
class CatalogueFamilyOverTheProtocolTest {

    // ----------------------------------------------------------------------------------
    // The postings
    // ----------------------------------------------------------------------------------

    @Test
    fun `list_transactions returns the month's postings with the ledger's totals beside them`() = runTest {
        withCatalogue { client ->
            val payload = client.callTool("list_transactions", MARCH).payload()

            assertEquals(POSTINGS_IN_MARCH, payload.number("matching")?.toInt())
            assertEquals(POSTINGS_IN_MARCH, payload.number("returned")?.toInt())
            assertEquals(false, payload.flag("has_more"))
            assertEquals("date", payload.text("ordered_by"))
            assertNull(payload["read_from"], "no account was named, so nothing was read from one")

            assertEquals(MarchWorld.SALARY, payload.at("totals", "income").amount())
            assertEquals(SPENT_IN_MARCH, payload.at("totals", "expense").amount())
            assertTrue(
                "ledger" in payload.at("totals").text("basis").orEmpty(),
                "the totals do not say where they came from: ${payload.at("totals")}",
            )
        }
    }

    @Test
    fun `get_transaction answers every monetary leg, and a listing answers one`() = runTest {
        withCatalogue { client ->
            val line = client
                .callTool("list_transactions", """{"month":"2026-03","nature":"transfer"}""")
                .payload()
                .items("transactions")
                .single()

            // The line of a list is a single leg, because a list is a list of lines.
            assertEquals(MarchWorld.TRANSFERRED, line.at("amount").amount())

            val detail = client
                .callTool("get_transaction", """{"id":${line.number("id")?.toInt()}}""")
                .payload()

            assertEquals("transfer", detail.text("nature"))
            assertEquals(
                listOf("Nubank" to -MarchWorld.TRANSFERRED, "Poupança" to MarchWorld.TRANSFERRED),
                detail.items("legs").map { it.text("name") to it.at("amount").amount() },
                "an operation has both of its ends, signed as the ledger recorded them",
            )
            assertEquals(
                listOf("expense", "income"),
                detail.items("legs").map { it.text("direction") },
            )
            assertNull(detail["applied_rate"], "nothing crossed a currency, so no rate was applied")
        }
    }

    @Test
    fun `get_transaction carries the rate the operation itself got when its legs cross currencies`() = runTest {
        crossCurrency().use { world ->
            world.overTheProtocol { client ->
                val detail = client.callTool("get_transaction", """{"id":1}""").payload()

                assertEquals("transfer", detail.text("nature"))
                assertEquals(
                    listOf("BRL" to -550.0, "USD" to 100.0),
                    detail.items("legs").map { it.at("amount").currency() to it.at("amount").amount() },
                    "each leg is exact in its own account's currency; nothing was converted",
                )

                val rate = detail.at("applied_rate")
                assertEquals("BRL", rate.text("from"))
                assertEquals("USD", rate.text("to"))
                assertEquals(
                    100.0 / 550.0,
                    rate.number("rate"),
                    "the rate is the quotient of this operation's own two ends",
                )
            }
        }
    }

    @Test
    fun `an identity that matches nothing is refused by name, not answered emptily`() = runTest {
        withCatalogue { client ->
            listOf(
                "get_transaction" to """{"id":404}""",
                "get_invoice" to """{"id":404}""",
                "list_transactions" to """{"account_id":404}""",
                "list_invoices" to """{"card_id":404}""",
            ).forEach { (tool, arguments) ->
                val response = client.callTool(tool, arguments)
                assertTrue(response.isToolError(), "`$tool` did not flag the refusal as an error")
                assertTrue(
                    "404" in response.payload().text("reason").orEmpty(),
                    "`$tool` does not say which identity was not found: ${response.toolText()}",
                )
            }
        }
    }

    @Test
    fun `a listing read from two points of view at once is refused rather than guessed at`() = runTest {
        withCatalogue { client ->
            val response = client
                .callTool("list_transactions", """{"account_id":1,"card_id":1}""")

            assertTrue(response.isToolError())
            assertTrue(
                "account_id" in response.payload().text("reason").orEmpty(),
                "the refusal does not name the arguments that conflict: ${response.toolText()}",
            )
        }
    }

    // ----------------------------------------------------------------------------------
    // The facades
    // ----------------------------------------------------------------------------------

    @Test
    fun `list_accounts answers each balance in its own currency, and the ledger's total`() = runTest {
        withCatalogue { client ->
            val payload = client.callTool("list_accounts", MARCH).payload()

            assertEquals(
                listOf("Nubank" to MarchWorld.ACCOUNT_BALANCE - MarchWorld.TRANSFERRED, "Poupança" to MarchWorld.TRANSFERRED),
                payload.items("accounts").map { it.text("name") to it.at("balance").amount() },
            )
            assertEquals("BRL", payload.items("accounts").first().at("balance").currency())
            assertEquals(
                MarchWorld.ACCOUNT_BALANCE,
                payload.at("total").amount(),
                "the total is the ledger's over the same accounts",
            )
            assertTrue(
                "card" in payload.at("perimeter").toString(),
                "the answer does not say card debt is outside it: ${payload.at("perimeter")}",
            )
        }
    }

    @Test
    fun `list_cards answers the limit, what is used and what is left, without naming an invoice`() = runTest {
        withCatalogue { client ->
            val card = client.callTool("list_cards").payload().items("cards").single()

            assertEquals("Cartão", card.text("name"))
            assertEquals(MarchWorld.CARD_LIMIT, card.at("limit").amount())
            assertEquals(CARD_OWED_WITH_PLAN, card.at("used").amount())
            assertEquals(MarchWorld.CARD_LIMIT - CARD_OWED_WITH_PLAN, card.at("available").amount())
            assertEquals(20, card.number("closing_day")?.toInt())
            assertEquals(28, card.number("due_day")?.toInt())
        }
    }

    @Test
    fun `list_categories keeps the categories nothing moved under, which a breakdown drops`() = runTest {
        withCatalogue { client ->
            val payload = client.callTool("list_categories", MARCH).payload()
            val totals = payload.items("categories").associate { it.text("name") to it.at("total").amount() }

            assertEquals(
                mapOf<String?, Double?>(
                    "Eletrônicos" to PLAN_TOTAL,
                    "Mercado" to MarchWorld.SPENT,
                    "Salário" to MarchWorld.SALARY,
                ),
                totals,
            )
            assertTrue(
                payload.items("categories").all { it["share"] == null },
                "a catalogue reports no share: a share needs the whole, and this list has no line " +
                    "for the money that carries no category",
            )

            val quiet = client
                .callTool("list_categories", """{"month":"2026-02"}""")
                .payload()
                .items("categories")
            assertEquals(
                listOf(0.0, 0.0, 0.0),
                quiet.map { it.at("total").amount() },
                "a month nothing moved in still lists every category, at zero",
            )
        }
    }

    // ----------------------------------------------------------------------------------
    // The invoices
    // ----------------------------------------------------------------------------------

    @Test
    fun `list_invoices answers what each owes, and the total over every matching one`() = runTest {
        withCatalogue { client ->
            val payload = client.callTool("list_invoices").payload()
            val invoice = payload.items("invoices").single()

            assertEquals("Cartão", invoice.text("card"))
            assertEquals("open", invoice.text("status"))
            assertEquals(CARD_OWED_WITH_PLAN, invoice.at("owed").amount())
            assertEquals("BRL", invoice.at("owed").currency())
            assertEquals(CARD_OWED_WITH_PLAN, payload.at("owed_total").amount())
            assertEquals(1, payload.number("matching")?.toInt())
            assertEquals(false, payload.flag("has_more"))
        }
    }

    /**
     * Several invoices at once: the owed figure of each comes from the **batched** ledger read, and
     * the total is the whole filter's rather than the page's.
     *
     * Both facts need more than one invoice to be visible at all — with one, a per-invoice read in a
     * loop and a single grouped query are indistinguishable, and a total taken from the page is the
     * right number by accident.
     */
    @Test
    fun `many invoices come back owed by owed, and the total is every one of them`() = runTest {
        manyInvoices().use { world ->
            world.overTheProtocol { client ->
                val payload = client.callTool("list_invoices", """{"card_id":1,"limit":2}""").payload()

                assertEquals(3, payload.number("matching")?.toInt())
                assertEquals(2, payload.number("returned")?.toInt())
                assertEquals(true, payload.flag("has_more"))
                assertEquals(
                    listOf("2026-03-28" to 300.00, "2026-02-28" to 200.00),
                    payload.items("invoices").map { it.text("due_date") to it.at("owed").amount() },
                    "newest cycle first, each with what the ledger says it owes",
                )
                assertEquals(
                    600.00,
                    payload.at("owed_total").amount(),
                    "the total is every matching invoice's, not the two on this page",
                )

                assertEquals(
                    1,
                    client.callTool("list_invoices", """{"card_id":1,"status":"open"}""")
                        .payload()
                        .number("matching")
                        ?.toInt(),
                )
            }
        }
    }

    @Test
    fun `list_invoices narrowed to a state it holds none of answers emptily rather than failing`() = runTest {
        withCatalogue { client ->
            val payload = client.callTool("list_invoices", """{"status":"paid"}""").payload()

            assertEquals(0, payload.number("matching")?.toInt())
            assertEquals(0, payload.items("invoices").size)
            assertNotNull(payload["owed_total"], "a total of nothing is still a figure")
        }
    }

    @Test
    fun `get_invoice answers the window, the breakdown and the statement read from the card`() = runTest {
        withCatalogue { client ->
            val payload = client.callTool("get_invoice", """{"id":1}""").payload()

            assertEquals("2026-02-20", payload.at("period").text("from"), "the cycle opened a month before")
            assertEquals(
                "2026-03-19",
                payload.at("period").text("to"),
                "the cycle's last day, not the closing date — a purchase on the 20th is the next one's",
            )
            assertEquals("2026-03-28", payload.at("invoice").text("due_date"))

            assertEquals(MarchWorld.GROCERIES_ON_CARD + PLAN_TOTAL, payload.at("spent").amount())
            assertEquals(MarchWorld.INVOICE_PAID, payload.at("advance_payments").amount())
            assertEquals(CARD_OWED_WITH_PLAN, payload.at("invoice", "owed").amount())

            val statement = payload.items("statement")
            assertEquals(POSTINGS_ON_THE_INVOICE, statement.size)
            assertEquals(
                listOf("income", "expense", "expense", "expense", "expense"),
                statement.map { it.text("direction") },
                "read from the card: the payment on the 13th came into it, and every purchase " +
                    "before that was charged to it",
            )
        }
    }

    /**
     * **The day the cycle closes** — the day the question is actually asked on.
     *
     * The window is half-open, so the closing day is the first day it refuses: a purchase made on
     * it starts the next cycle. An answer that handed that day to the period would say the cycle is
     * still taking postings, beside a status saying it closed, and would name a day the figures
     * could never reach.
     */
    @Test
    fun `get_invoice on the closing day does not answer a closed cycle as still running`() = runTest {
        AgentWorld(today = LocalDate(2026, 3, 20)).use { world ->
            val card = world.card(
                id = 1,
                accountId = MarchWorld.ACCOUNT_CARD,
                name = "Cartão",
                limit = MarchWorld.CARD_LIMIT,
            )
            world.invoice(
                id = 1,
                dimensionId = MarchWorld.DIMENSION_INVOICE,
                card = card,
                month = MarchWorld.MONTH,
                status = Invoice.Status.CLOSED,
            )

            world.overTheProtocol { client ->
                val payload = client.callTool("get_invoice", """{"id":1}""").payload()
                val period = payload.at("period")
                val closingDate = LocalDate.parse(payload.at("invoice").text("closing_date")!!)

                assertEquals("closed", payload.at("invoice").text("status"))
                assertEquals(
                    false,
                    period.flag("is_in_progress"),
                    "the cycle closed today and the same payload says it is still running: $period",
                )
                assertTrue(
                    LocalDate.parse(period.text("to")!!) < closingDate,
                    "`to` is the last day the period covers, and $closingDate is the first day " +
                        "the window refuses: $period",
                )
                assertTrue(
                    LocalDate.parse(period.text("measured_through")!!) < closingDate,
                    "the figures are said to reach a day no posting on this invoice can land on: $period",
                )
            }
        }
    }

    /**
     * **The two tools that answer about invoices say which cut they offer** — the requirement that
     * choosing between them must not cost a call to each.
     *
     * They genuinely overlap: both mention invoices, and one of the two answers an invoice that the
     * other also answers. What separates them is the shape, and the shape is what each description
     * has to state.
     */
    @Test
    fun `list_invoices and get_card_overview each declare their cut, and name the other`() = runTest {
        withCatalogue { client ->
            val announced = client.listTools()
            val invoices = announced.announcedDescription(McpToolName.LIST_INVOICES.wireName)
            val cards = announced.announcedDescription(McpToolName.GET_CARD_OVERVIEW.wireName)

            assertTrue(
                McpToolName.GET_CARD_OVERVIEW.wireName in invoices,
                "`list_invoices` does not name the neighbouring tool:\n$invoices",
            )
            assertTrue(
                McpToolName.LIST_INVOICES.wireName in cards,
                "`get_card_overview` does not name the neighbouring tool:\n$cards",
            )
            assertTrue(
                "INVOICES" in invoices && "CARDS" in cards,
                "the two do not say which of the two shapes each answers in:\n$invoices\n\n$cards",
            )
            assertTrue(
                "closed" in invoices && "open at this moment" in cards,
                "the two do not say which cycles each reaches:\n$invoices\n\n$cards",
            )
        }
    }

    // ----------------------------------------------------------------------------------
    // The plans, the budgets and the templates
    // ----------------------------------------------------------------------------------

    @Test
    fun `list_installments counts an instalment as paid when its invoice was paid`() = runTest {
        withCatalogue { client ->
            val plan = client.callTool("list_installments").payload().items("installments").single()

            assertEquals("Fone", plan.text("title"))
            assertEquals("Cartão", plan.text("card"))
            assertEquals(PLAN_COUNT, plan.number("count")?.toInt())
            assertEquals(
                0,
                plan.number("paid")?.toInt(),
                "the invoice carrying them is open, so none of them is paid — whatever their dates",
            )
            assertEquals(PLAN_COUNT, plan.number("remaining")?.toInt())
            assertEquals(PLAN_TOTAL, plan.at("total").amount())
            assertEquals(PLAN_TOTAL / PLAN_COUNT, plan.at("installment_amount").amount())

            assertEquals(
                0,
                client.callTool("list_installments", """{"status":"completed"}""")
                    .payload()
                    .items("installments")
                    .size,
            )
        }
    }

    @Test
    fun `list_budgets answers the limit as it was set, with no month in it`() = runTest {
        withCatalogue { client ->
            val budget = client.callTool("list_budgets").payload().items("budgets").single()

            assertEquals("Mercado do mês", budget.text("title"))
            assertEquals(BUDGET_LIMIT, budget.at("limit").amount())
            assertEquals("BRL", budget.at("limit").currency())
            assertEquals(listOf("Mercado"), budget["categories"]!!.jsonArray.map { it.toString().trim('"') })
            assertNull(budget["spent"], "what was spent is a question about a month, and this has none")
            assertNull(budget["progress"])
        }
    }

    @Test
    fun `list_recurring keeps the templates a pending listing drops, and marks which are waiting`() = runTest {
        withCatalogue { client ->
            val templates = client.callTool("list_recurring").payload().items("recurring")

            assertEquals(listOf("Aluguel", "Streaming"), templates.map { it.text("title") })
            assertEquals(
                mapOf<String?, Boolean?>("Aluguel" to false, "Streaming" to true),
                templates.associate { it.text("title") to it.flag("is_pending") },
                "the one whose day has not arrived is here too, and says it is not waiting",
            )

            val pending = client.callTool("get_pending_recurring").payload()
            assertEquals(
                listOf("Streaming"),
                pending.items("pending").map { it.text("title") },
                "the narrower tool answers only the ones due — which is what separates the two",
            )
        }
    }

    // ----------------------------------------------------------------------------------

    /** A seeded March with a plan, a budget and two templates on top of it. */
    private suspend fun withCatalogue(block: suspend (McpConversation) -> Unit) {
        AgentWorld().use { world ->
            val card = world.seedMarch()

            world.ledgerAccount(ELECTRONICS_ACCOUNT, AccountEntity.Type.EXPENSE, "Eletrônicos")
            val electronics = world.category(id = 3, dimensionId = 3, name = "Eletrônicos")

            world.installments += Installment(id = 1, count = PLAN_COUNT, totalAmount = PLAN_TOTAL)
            repeat(PLAN_COUNT) { index ->
                world.posting(
                    "2026-03-1${index}",
                    (MarchWorld.ACCOUNT_CARD posts -PLAN_INSTALMENT_CENTS)
                        .taggedWith(MarchWorld.DIMENSION_INVOICE),
                    (ELECTRONICS_ACCOUNT posts PLAN_INSTALMENT_CENTS).taggedWith(electronics.dimensionId),
                    title = "Fone",
                    installmentId = 1,
                    installmentNumber = index + 1,
                )
            }

            world.budgets += Budget(
                id = 1,
                title = "Mercado do mês",
                categories = listOf(world.categories.first { it.name == "Mercado" }),
                iconKey = "tag",
                amount = BUDGET_LIMIT,
                currency = AgentWorld.BRL,
                createdAt = 0,
            )

            val account = world.accounts.first { it.id == MarchWorld.ACCOUNT_CHECKING }
            world.recurringList += Recurring(
                id = 1,
                type = TransactionType.EXPENSE,
                amount = 39.90,
                title = "Streaming",
                dayOfMonth = 10,
                category = null,
                account = account,
                creditCard = null,
                createdAt = 0,
            )
            world.recurringList += Recurring(
                id = 2,
                type = TransactionType.EXPENSE,
                amount = 1_800.0,
                title = "Aluguel",
                dayOfMonth = 25,
                category = null,
                account = account,
                creditCard = card,
                createdAt = 0,
            )

            world.overTheProtocol(block)
        }
    }

    /** One card, three cycles, and a purchase on each of them. */
    private suspend fun manyInvoices(): AgentWorld {
        val world = AgentWorld()
        val card = world.card(id = 1, accountId = 10, name = "Cartão", limit = 5_000.0)
        world.ledgerAccount(100, AccountEntity.Type.EXPENSE, "Despesas")

        listOf(
            Triple(YearMonth(2026, 1), 11L, 10_000L),
            Triple(YearMonth(2026, 2), 12L, 20_000L),
            Triple(YearMonth(2026, 3), 13L, 30_000L),
        ).forEachIndexed { index, (month, dimensionId, cents) ->
            world.invoice(
                id = index + 1L,
                dimensionId = dimensionId,
                card = card,
                month = month,
                status = if (month == YearMonth(2026, 3)) Invoice.Status.OPEN else Invoice.Status.CLOSED,
            )
            world.posting(
                "${month}-15",
                (10L posts -cents).taggedWith(dimensionId),
                100L posts cents,
            )
        }
        return world
    }

    /** Five hundred and fifty reais leaving one account and a hundred dollars arriving in another. */
    private suspend fun crossCurrency(): AgentWorld {
        val world = AgentWorld(rates = mapOf("USD" to 5.5), currenciesInUse = listOf("BRL", "USD"))
        world.account(1, "Nubank", currency = "BRL")
        world.account(2, "Wise", currency = "USD")
        world.ledgerAccount(900, AccountEntity.Type.CONVERSION, "Conversão BRL", currency = "BRL")
        world.ledgerAccount(901, AccountEntity.Type.CONVERSION, "Conversão USD", currency = "USD")

        world.posting(
            "2026-03-08",
            1L posts -55_000,
            900L posts 55_000,
            (2L posts 10_000) inCurrency "USD",
            (901L posts -10_000) inCurrency "USD",
        )
        return world
    }

    private fun JsonObject.items(field: String): List<JsonObject> =
        this[field]!!.jsonArray.map { it.jsonObject }

    private companion object {

        const val MARCH = """{"month":"2026-03"}"""

        const val ELECTRONICS_ACCOUNT = 101L

        const val PLAN_COUNT = 3
        const val PLAN_INSTALMENT_CENTS = 9_000L
        const val PLAN_TOTAL = 270.00

        const val BUDGET_LIMIT = 800.00

        /** March's five postings, plus the three instalments. */
        const val POSTINGS_IN_MARCH = 8

        /** The card's purchase, its payment, and the three instalments. */
        const val POSTINGS_ON_THE_INVOICE = 5

        const val SPENT_IN_MARCH = MarchWorld.SPENT + PLAN_TOTAL
        const val CARD_OWED_WITH_PLAN = MarchWorld.CARD_OWED + PLAN_TOTAL
    }
}
