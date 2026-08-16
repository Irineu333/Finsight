package com.neoutils.finsight.mcp

import com.neoutils.finsight.mcp.McpServerHarness.Companion.freePort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * **Every question the family answers, driven over a real socket.**
 *
 * Each tool is called the way a client calls it — `initialize`, `notifications/initialized`,
 * `tools/list`, `tools/call` — against a server bound to loopback, with a real ledger behind it.
 * Calling the Kotlin functions directly would prove the composition and none of the path: not the
 * schema the SDK validates arguments against, not the serialisation of the payload, and not the
 * error flag a refusal has to arrive with.
 */
class QuestionsFamilyOverTheProtocolTest {

    @Test
    fun `the server announces exactly the ten questions, each with what it covers`() = runTest {
        withWorld { _, client ->
            val announced = client.listTools()

            assertEquals(
                McpSurface.offered.map { it.wireName }.sorted(),
                announced.announcedToolNames().sorted(),
                "The socket and the declaration disagree about what is offered.",
            )

            // Design D14 and the perimeter requirement: the description is the only material an
            // agent has before it chooses, so every aggregate figure states its perimeter there.
            McpSurface.offered.forEach { tool ->
                val description = announced.announcedDescription(tool.wireName)
                assertTrue(
                    "PERIMETER:" in description,
                    "`${tool.wireName}` does not say what its figures cover:\n$description",
                )
            }

            // The one confusion the requirement names outright: a total of balances and a net worth
            // read identically and are different numbers.
            val balance = announced.announcedDescription(McpToolName.GET_BALANCE.wireName)
            assertTrue(
                "get_net_worth" in balance && "NOT" in balance,
                "`get_balance` does not say that card debt is left out, nor name what subtracts " +
                    "it:\n$balance",
            )
        }
    }

    @Test
    fun `get_balance answers the money in the accounts, and says the card debt is not in it`() = runTest {
        withWorld { _, client ->
            val payload = client.callTool("get_balance", """{"month":"2026-03"}""").payload()

            assertEquals(MarchWorld.ACCOUNT_BALANCE, payload.at("balance").amount())
            assertEquals("BRL", payload.at("balance").currency())

            val perimeter = payload.at("perimeter")
            assertTrue(
                perimeter["excludes"]?.jsonArray.orEmpty().any { "card" in it.toString() },
                "The answer does not say card debt is outside it: $perimeter",
            )
            assertTrue(
                McpToolName.GET_NET_WORTH.wireName in perimeter.toString(),
                "The answer does not name the reading that subtracts the debt: $perimeter",
            )
        }
    }

    @Test
    fun `get_balance scoped to one account is exact and in that account's own currency`() = runTest {
        withWorld { _, client ->
            val payload = client
                .callTool("get_balance", """{"month":"2026-03","account_id":2}""")
                .payload()

            assertEquals(MarchWorld.TRANSFERRED, payload.at("balance").amount())
            assertEquals(false, payload.at("balance").flag("is_approximate"))
            assertEquals("Poupança", payload.at("account").text("name"))
        }
    }

    @Test
    fun `get_balance refuses an account that does not exist, and names it`() = runTest {
        withWorld { _, client ->
            val response = client.callTool("get_balance", """{"account_id":404}""")

            assertTrue(response.isToolError(), "A refusal has to reach the agent as an error.")
            assertTrue(
                "account" in response.payload().text("reason").orEmpty() &&
                    "404" in response.payload().text("reason").orEmpty(),
                "The refusal does not say which identity was not found: ${response.toolText()}",
            )
        }
    }

    @Test
    fun `get_net_worth subtracts the card debt from the accounts`() = runTest {
        withWorld { _, client ->
            val payload = client.callTool("get_net_worth").payload()

            assertEquals(MarchWorld.NET_WORTH, payload.at("net_worth").amount())
            assertEquals("BRL", payload.at("net_worth").currency())
            assertTrue(
                MarchWorld.NET_WORTH < MarchWorld.ACCOUNT_BALANCE,
                "the fixture no longer separates the two figures, so this proves nothing",
            )
        }
    }

    @Test
    fun `get_month_summary reports the month's income and both halves of its spending`() = runTest {
        withWorld { _, client ->
            val payload = client.callTool("get_month_summary", """{"month":"2026-03"}""").payload()

            assertEquals(MarchWorld.SALARY, payload.at("income").amount())
            assertEquals(MarchWorld.GROCERIES_FROM_ACCOUNT, payload.at("expense_from_accounts").amount())
            assertEquals(MarchWorld.GROCERIES_ON_CARD, payload.at("expense_on_cards").amount())
            assertEquals(MarchWorld.SPENT, payload.at("expense").amount())
            assertEquals(MarchWorld.INVOICE_PAID, payload.at("invoice_payments").amount())
            assertEquals(MarchWorld.NET_WORTH, payload.at("closing_net").amount())
        }
    }

    @Test
    fun `get_category_spending ranks the categories and totals the month`() = runTest {
        withWorld { _, client ->
            val payload = client.callTool("get_category_spending", """{"month":"2026-03"}""").payload()

            assertEquals("expense", payload.text("nature"))
            assertEquals(MarchWorld.SPENT, payload.at("total").amount())

            val categories = payload["categories"]?.jsonArray.orEmpty().map { it.jsonObject }
            assertEquals(listOf("Mercado"), categories.map { it.text("name") })
            assertEquals(MarchWorld.SPENT, categories.single().at("total").amount())
            assertEquals(1.0, categories.single().number("share"))
        }
    }

    @Test
    fun `get_category_income answers the other side of the ledger`() = runTest {
        withWorld { _, client ->
            val payload = client.callTool("get_category_income", """{"month":"2026-03"}""").payload()

            assertEquals("income", payload.text("nature"))
            assertEquals(MarchWorld.SALARY, payload.at("total").amount())
            assertEquals(
                listOf("Salário"),
                payload["categories"]?.jsonArray.orEmpty().map { it.jsonObject.text("name") },
            )
        }
    }

    @Test
    fun `get_spending_breakdown answers both sides and the net between them`() = runTest {
        withWorld { _, client ->
            val payload = client.callTool("get_spending_breakdown", """{"month":"2026-03"}""").payload()

            val natures = payload["breakdowns"]?.jsonArray.orEmpty().map { it.jsonObject.text("nature") }
            assertEquals(listOf("expense", "income"), natures)
            assertEquals(MarchWorld.SALARY - MarchWorld.SPENT, payload.at("net").amount())
        }
    }

    @Test
    fun `get_spending_breakdown narrowed to one nature has no net to state`() = runTest {
        withWorld { _, client ->
            val payload = client
                .callTool("get_spending_breakdown", """{"month":"2026-03","nature":"expense"}""")
                .payload()

            assertEquals(1, payload["breakdowns"]?.jsonArray.orEmpty().size)
            assertEquals(
                null,
                payload["net"],
                "One side alone has no net; a zero there would be a number nobody measured.",
            )
        }
    }

    @Test
    fun `a value the discriminator refuses is refused by name, not guessed at`() = runTest {
        withWorld { _, client ->
            val response = client
                .callTool("get_spending_breakdown", """{"month":"2026-03","nature":"transfers"}""")

            assertTrue(response.isToolError())
            assertTrue(
                "nature" in response.payload().text("reason").orEmpty(),
                "The refusal does not name the argument it rejected: ${response.toolText()}",
            )
        }
    }

    @Test
    fun `get_card_overview answers the limit, what is owed, and the invoice open now`() = runTest {
        withWorld { _, client ->
            val payload = client.callTool("get_card_overview").payload()

            val overview = payload["cards"]!!.jsonArray.single().jsonObject
            assertEquals("Cartão", overview.at("card").text("name"))
            assertEquals(MarchWorld.CARD_LIMIT, overview.at("card", "limit").amount())
            assertEquals(MarchWorld.CARD_OWED, overview.at("card", "used").amount())
            assertEquals(MarchWorld.CARD_LIMIT - MarchWorld.CARD_OWED, overview.at("card", "available").amount())
            assertEquals("open", overview.at("open_invoice").text("status"))
            assertEquals(MarchWorld.CARD_OWED, overview.at("open_invoice", "owed").amount())
        }
    }

    @Test
    fun `get_report_stats answers a range seen from the accounts`() = runTest {
        withWorld { _, client ->
            val payload = client
                .callTool("get_report_stats", """{"from":"2026-03-01","to":"2026-03-31"}""")
                .payload()

            assertEquals("accounts", payload.text("scope"))
            assertEquals(MarchWorld.SALARY, payload.at("income").amount())
            // A different perimeter from `get_month_summary`, and deliberately so: money is counted
            // as it crosses the boundary of the chosen accounts, and paying the card crosses it.
            // The declared perimeter has to say that, or the two figures look like a contradiction.
            assertEquals(
                MarchWorld.GROCERIES_FROM_ACCOUNT + MarchWorld.INVOICE_PAID,
                payload.at("expense").amount(),
            )
            assertTrue(
                "card" in payload.at("perimeter").text("covers").orEmpty(),
                "The answer does not say the card payment is counted as an outflow here: " +
                    payload.at("perimeter"),
            )
        }
    }

    @Test
    fun `get_report_stats refuses a range that runs backwards`() = runTest {
        withWorld { _, client ->
            val response = client
                .callTool("get_report_stats", """{"from":"2026-03-31","to":"2026-03-01"}""")

            assertTrue(response.isToolError())
            assertTrue("before" in response.payload().text("reason").orEmpty())
        }
    }

    @Test
    fun `get_budget_progress and get_pending_recurring answer emptily rather than failing`() = runTest {
        withWorld { _, client ->
            val budgets = client.callTool("get_budget_progress", """{"month":"2026-03"}""").payload()
            assertEquals(0, budgets["budgets"]?.jsonArray.orEmpty().size)

            val pending = client.callTool("get_pending_recurring").payload()
            assertEquals(0, pending["pending"]?.jsonArray.orEmpty().size)
            assertNotNull(pending["expected_total"], "a total of nothing is still a figure")
        }
    }

    /**
     * **Every answer of the family declares its perimeter, and every period says whether it ended.**
     *
     * Mechanical rather than one assertion per tool, so an eleventh question added later cannot slip
     * through without either. Both facts are unrecoverable from the payload once they are missing:
     * `18.400,00` is the same number whether or not card debt was taken off it, and two totals with
     * no mark of completeness read as a fall in spending when one month simply has not finished.
     */
    @Test
    fun `every answer declares its perimeter, and every period says whether it has finished`() = runTest {
        withWorld { _, client ->
            McpSurface.offered.forEach { tool ->
                val payload = client.callTool(tool.wireName, MINIMAL_ARGUMENTS[tool] ?: "{}").payload()

                assertNotNull(
                    payload["perimeter"],
                    "`${tool.wireName}` answered a figure without saying what it covers: $payload",
                )

                listOf("period", "as_of").mapNotNull { payload[it] }.forEach { period ->
                    val fields = period.jsonObject
                    assertNotNull(
                        fields["is_in_progress"],
                        "`${tool.wireName}` answered about a period without saying whether it " +
                            "has finished: $fields",
                    )
                    assertNotNull(
                        fields["measured_through"],
                        "`${tool.wireName}` does not say through which day its period is " +
                            "measured: $fields",
                    )
                }
            }
        }
    }

    /** A question never leaves a trace: the log is for what changed, and none of these change anything. */
    @Test
    fun `a run of questions writes nothing to the activity log`() = runTest {
        withWorld { harness, client ->
            client.callTool("get_balance")
            client.callTool("get_net_worth")
            client.callTool("get_month_summary")

            assertEquals(
                emptyList(),
                harness.activity.observeAll().first(),
                "A question was written to the log.",
            )
        }
    }

    // ----------------------------------------------------------------------------------

    /** A seeded March, a bound socket, and a conversation already past the handshake. */
    private suspend fun withWorld(block: suspend (McpServerHarness, McpConversation) -> Unit) {
        AgentWorld().use { world ->
            world.seedMarch()
            val port = freePort()

            McpServerHarness(tools = world.tools()).use { harness ->
                harness.controller.setPort(port)
                harness.controller.setEnabled(true)
                val token = assertNotNull(harness.controller.token.value)

                withContext(Dispatchers.IO) {
                    block(harness, McpConversation(port, token).open())
                }

                harness.controller.stop()
            }
        }
    }

    private companion object {

        /**
         * The least a caller can say and still be answered. Empty for every tool that defaults to
         * the month the app is in, which is what a person means by "this month".
         */
        val MINIMAL_ARGUMENTS = mapOf(
            McpToolName.GET_REPORT_STATS to """{"from":"2026-03-01","to":"2026-03-31"}""",
        )
    }
}
