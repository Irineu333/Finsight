package com.neoutils.finsight.mcp

import com.neoutils.finsight.database.entity.AccountEntity
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What a month's totals contain, what they say about themselves, and what they never contain.
 *
 * The last one is the trap the simulation that shaped this surface walked into and did not fall for:
 * a transfer between the user's own accounts and the payment of a card invoice both move real money,
 * and neither is spending. **Nothing in the tool filters them.** The ledger's own aggregate is
 * defined as "the transactions with a nominal or equity counter-leg", which is exactly "not a
 * transfer and not a card payment" — so a tool that had to filter would be reading from the wrong
 * place.
 */
class MonthSummaryTest {

    /**
     * A month whose only two postings are the two that are not spending: the totals are zero, and
     * the balances still moved.
     *
     * Stated this way rather than by subtracting from a larger figure, because a filter applied
     * *after* the aggregate would pass the subtraction and fail this.
     */
    @Test
    fun `a transfer and a card payment are in no total, though both moved money`() = runTest {
        AgentWorld().use { world ->
            world.account(1, "Nubank")
            world.account(2, "Poupança")
            world.card(id = 1, accountId = 10, name = "Cartão")

            // Money between the user's own accounts.
            world.posting("2026-03-11", 1L posts -100_000, 2L posts 100_000)
            // Money settling a debt already counted when it was spent.
            world.posting("2026-03-13", 1L posts -15_000, 10L posts 15_000)

            world.overTheProtocol { client ->
                val summary = client.callTool("get_month_summary", MARCH).payload()

                assertEquals(0.0, summary.at("expense").amount(), "a transfer or a payment was spent")
                assertEquals(0.0, summary.at("expense_from_accounts").amount())
                assertEquals(0.0, summary.at("expense_on_cards").amount())
                assertEquals(0.0, summary.at("income").amount(), "neither of them is income either")

                // The control: the postings are real, and the money did move.
                assertEquals(
                    1_000.00,
                    client.callTool("get_balance", """{"month":"2026-03","account_id":2}""")
                        .payload().at("balance").amount(),
                    "the transfer never happened, so the assertions above prove nothing",
                )
                assertEquals(
                    150.00,
                    summary.at("invoice_payments").amount(),
                    "the payment is reported — beside the totals, never inside them",
                )
            }
        }
    }

    /** The same month with real spending in it: the totals are the spending, and nothing more. */
    @Test
    fun `the total of a month holding both is exactly the spending`() = runTest {
        AgentWorld().use { world ->
            world.seedMarch()

            world.overTheProtocol { client ->
                val summary = client.callTool("get_month_summary", MARCH).payload()

                assertEquals(MarchWorld.SPENT, summary.at("expense").amount())
                assertEquals(MarchWorld.SALARY, summary.at("income").amount())
            }

            assertTrue(
                MarchWorld.TRANSFERRED > MarchWorld.SPENT && MarchWorld.INVOICE_PAID > 0.0,
                "the fixture no longer holds a transfer and a payment big enough to notice",
            )
        }
    }

    /** And the announced description says so, which is where an agent reads it before it chooses. */
    @Test
    fun `the announced description says the two are outside the totals`() = runTest {
        AgentWorld().use { world ->
            world.overTheProtocol { client ->
                val description = client.listTools()
                    .announcedDescription(McpToolName.GET_MONTH_SUMMARY.wireName)

                assertTrue("transfer" in description.lowercase(), description)
                assertTrue("payment" in description.lowercase(), description)
                assertTrue("NOT" in description, description)
            }
        }
    }

    // ----------------------------------------------------------------------------------
    // A period says whether it has finished
    // ----------------------------------------------------------------------------------

    @Test
    fun `the month the app is in is marked in progress, and says through which day`() = runTest {
        AgentWorld(today = LocalDate(2026, 3, 15)).use { world ->
            world.seedMarch()

            world.overTheProtocol { client ->
                val period = client.callTool("get_month_summary", MARCH).payload().at("period")

                assertEquals(true, period.flag("is_in_progress"))
                assertEquals("2026-03-15", period.text("measured_through"))
                assertEquals("2026-03-31", period.text("to"))
            }
        }
    }

    @Test
    fun `a month that has ended is not marked in progress`() = runTest {
        AgentWorld(today = LocalDate(2026, 3, 15)).use { world ->
            world.seedMarch()

            world.overTheProtocol { client ->
                val period = client.callTool("get_month_summary", """{"month":"2026-02"}""")
                    .payload().at("period")

                assertEquals(false, period.flag("is_in_progress"))
                assertEquals("2026-02-28", period.text("measured_through"))
            }
        }
    }

    // ----------------------------------------------------------------------------------
    // A comparison arrives already taken
    // ----------------------------------------------------------------------------------

    @Test
    fun `comparing two months returns the differences, and names the unfinished side`() = runTest {
        AgentWorld(today = LocalDate(2026, 3, 15)).use { world ->
            world.seedMarch()
            // February: a smaller grocery bill, and no salary at all.
            world.posting(
                "2026-02-10",
                MarchWorld.ACCOUNT_CHECKING posts -25_000,
                (MarchWorld.NOMINAL_EXPENSE posts 25_000).taggedWith(MarchWorld.DIMENSION_GROCERIES),
            )

            world.overTheProtocol { client ->
                val comparison = client
                    .callTool("get_month_summary", """{"month":"2026-03","compare_to":"2026-02"}""")
                    .payload()
                    .at("compared_to")

                assertEquals("2026-02", comparison.at("period").text("month"))
                assertEquals(
                    "this_period",
                    comparison.text("incomplete_side"),
                    "March has not finished, and a variation read as a trend is the failure this " +
                        "field exists to prevent.",
                )

                val expense = comparison.changeOf("expense_from_accounts")

                assertEquals(MarchWorld.GROCERIES_FROM_ACCOUNT, expense.at("current").amount())
                assertEquals(250.00, expense.at("compared").amount())
                assertEquals(
                    50.00,
                    expense.at("difference").amount(),
                    "the subtraction is the app's to do, not the agent's",
                )
                assertEquals(20.0, expense.number("percent_change"))
            }
        }
    }

    @Test
    fun `a change against nothing has no percentage rather than a zero`() = runTest {
        AgentWorld(today = LocalDate(2026, 4, 15)).use { world ->
            world.seedMarch()

            world.overTheProtocol { client ->
                val comparison = client
                    .callTool("get_month_summary", """{"month":"2026-03","compare_to":"2026-02"}""")
                    .payload()
                    .at("compared_to")

                assertNull(
                    comparison.text("incomplete_side"),
                    "both months had ended by April, so neither is the unfinished one",
                )

                val income = comparison.changeOf("income")

                assertEquals(MarchWorld.SALARY, income.at("current").amount())
                assertEquals(0.0, income.at("compared").amount())
                assertNull(
                    income.number("percent_change"),
                    "a rise from zero has no percentage; a zero there would claim nothing moved",
                )
            }
        }
    }

    // ----------------------------------------------------------------------------------
    // A figure says what it covers
    // ----------------------------------------------------------------------------------

    /**
     * The two figures a consumer cannot tell apart by looking: the same number means "money in the
     * accounts" in one and "money less what is owed" in the other.
     */
    @Test
    fun `the total of the accounts and the net worth are different figures, and each says so`() = runTest {
        AgentWorld().use { world ->
            world.seedMarch()

            world.overTheProtocol { client ->
                val balance = client.callTool("get_balance", MARCH).payload()
                val netWorth = client.callTool("get_net_worth").payload()

                assertEquals(MarchWorld.ACCOUNT_BALANCE, balance.at("balance").amount())
                assertEquals(MarchWorld.NET_WORTH, netWorth.at("net_worth").amount())

                val perimeter = balance.at("perimeter")
                assertTrue(
                    "card" in perimeter["excludes"]!!.jsonArray.joinToString(),
                    "the balance does not say card debt is outside it: $perimeter",
                )
                assertTrue(
                    McpToolName.GET_NET_WORTH.wireName in perimeter["see_also"]!!.jsonArray.joinToString(),
                    "the balance does not name the reading that subtracts the debt: $perimeter",
                )
            }
        }
    }

    /** Cut at a month, net worth is the two natures summed — a different read, still declared. */
    @Test
    fun `net worth can be cut at a month, and says which cut it used`() = runTest {
        AgentWorld().use { world ->
            world.seedMarch()
            world.ledgerAccount(400, AccountEntity.Type.INCOME, "Receitas futuras")
            // A posting dated after March: inside the all-time figure, outside the March cut.
            world.posting(
                "2026-05-02",
                MarchWorld.ACCOUNT_CHECKING posts 70_000,
                400L posts -70_000,
            )

            world.overTheProtocol { client ->
                val allTime = client.callTool("get_net_worth").payload()
                val cutAtMarch = client.callTool("get_net_worth", MARCH).payload()

                assertEquals(MarchWorld.NET_WORTH + 700.00, allTime.at("net_worth").amount())
                assertEquals(MarchWorld.NET_WORTH, cutAtMarch.at("net_worth").amount())
                assertTrue(
                    "future" in allTime.at("perimeter").text("covers").orEmpty(),
                    "the all-time figure does not say it counts postings dated in the future: " +
                        allTime.at("perimeter"),
                )
                assertEquals("2026-03", cutAtMarch.at("as_of").text("month"))
            }
        }
    }

    // ----------------------------------------------------------------------------------

    private fun kotlinx.serialization.json.JsonObject.changeOf(figure: String) =
        this["changes"]!!.jsonArray.map { it.jsonObject }.single { it.text("figure") == figure }

    private companion object {
        const val MARCH = """{"month":"2026-03"}"""
    }
}
