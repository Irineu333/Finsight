package com.neoutils.finsight.mcp

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **The four things a listing has to get right, and each of them fails silently.**
 *
 * *The total is not the page's.* A month with more postings than a page holds is the only fixture
 * that can tell the two apart, so that is the fixture: sixty-five postings, fifty returned, and a
 * total that is the sixty-five's. An agent does not scroll to check, so a total it can disagree with
 * the ledger about is one it will report anyway — and it will report it low, which reads as "you
 * spent less than you did".
 *
 * *The order is total.* A date has a **day**'s resolution, so a day holding sixty postings has sixty
 * factorial orders that are all "by date". Paging an unstable one repeats items and drops others,
 * and nothing about the payload says it happened. And "the last thing I entered" is a question the
 * date cannot answer at all, which is why the recording order is offered as a criterion of its own.
 *
 * *The vocabulary follows the point of view.* Without one, a transfer between the user's own
 * accounts is a **transfer**; with one it is an outflow of that account and an inflow of the other.
 * Reporting the direction of an arbitrarily chosen leg as a property of the posting is how transfers
 * end up in the expense list and the month reads as double what was spent.
 *
 * *The counters describe the answer.* A row the mapper cannot read is dropped — that is the contract
 * it declares — and a count taken before the drop describes a list nobody received. Nothing in the
 * payload would contradict it: the number and the array simply disagree, and only one of them is
 * ever read.
 */
class TransactionListingTest {

    // ----------------------------------------------------------------------------------
    // The total comes from the ledger, not from the page
    // ----------------------------------------------------------------------------------

    @Test
    fun `the total covers every matching posting, and summing the page would not reach it`() = runTest {
        crowdedMarch().use { world ->
            world.overTheProtocol { client ->
                val payload = client.callTool("list_transactions", MARCH).payload()

                assertEquals(POSTINGS, payload.number("matching")?.toInt())
                assertEquals(PAGE, payload.number("returned")?.toInt())
                assertEquals(true, payload.flag("has_more"), "there are more, and it has to say so")

                assertEquals(
                    SPENT,
                    payload.at("totals", "expense").amount(),
                    "the ledger's own figure for the whole month",
                )
                assertEquals(SALARY, payload.at("totals", "income").amount())

                // And the same figure by an independent route through the ledger: the tool that
                // exists to answer it. Two surfaces disagreeing about a month is the failure the
                // aggregate rule is written against, and a fixture constant alone cannot catch it.
                val summary = client.callTool("get_month_summary", MARCH).payload()
                assertEquals(
                    summary.at("expense").amount(),
                    payload.at("totals", "expense").amount(),
                )
                assertEquals(
                    summary.at("income").amount(),
                    payload.at("totals", "income").amount(),
                )

                // What a tool that summed what it returned would have answered. It is not a smaller
                // rounding of the truth: it is the answer to a different question, and the payload
                // gives no sign of which one it answered.
                val summedFromThePage = payload.items("transactions")
                    .filter { it.text("nature") == "expense" }
                    .sumOf { it.at("amount").amount() ?: 0.0 }

                assertEquals(SPENT_ON_THE_FIRST_PAGE, summedFromThePage)
                assertNotEquals(
                    SPENT,
                    summedFromThePage,
                    "the fixture stopped separating the two figures, so this proves nothing",
                )
            }
        }
    }

    @Test
    fun `the total follows the account the month is read from`() = runTest {
        crowdedMarch().use { world ->
            world.overTheProtocol { client ->
                val payload = client
                    .callTool("list_transactions", """{"month":"2026-03","account_id":1}""")
                    .payload()

                assertEquals("Nubank", payload.text("read_from"))
                assertEquals(
                    LEFT_THE_CHECKING_ACCOUNT,
                    payload.at("totals", "expense").amount(),
                    "seen from one account, money moved to another of the user's own accounts is " +
                        "an outflow — and paying the card is one too",
                )
                assertTrue(
                    "account_id or card_id" in payload.at("totals").toString(),
                    "the totals do not say they are narrowed by the account: ${payload.at("totals")}",
                )
            }
        }
    }

    // ----------------------------------------------------------------------------------
    // The order is total, and paging it loses nothing
    // ----------------------------------------------------------------------------------

    @Test
    fun `walking the pages returns every posting exactly once`() = runTest {
        crowdedMarch().use { world ->
            world.overTheProtocol { client ->
                val walked = mutableListOf<Int>()
                var offset = 0
                while (true) {
                    val payload = client
                        .callTool(
                            "list_transactions",
                            """{"month":"2026-03","limit":$STEP,"offset":$offset}""",
                        )
                        .payload()

                    walked += payload.items("transactions").mapNotNull { it.number("id")?.toInt() }
                    if (payload.flag("has_more") != true) break
                    offset += STEP
                }

                assertEquals(POSTINGS, walked.size, "paging lost or repeated a posting")
                assertEquals(POSTINGS, walked.distinct().size, "a posting came back on two pages")
                assertEquals((1..POSTINGS).toSet(), walked.toSet(), "a posting never came back")
            }
        }
    }

    @Test
    fun `two identical calls come back in the same order, though sixty postings share a date`() = runTest {
        crowdedMarch().use { world ->
            world.overTheProtocol { client ->
                fun ids() = client.callTool("list_transactions", MARCH).payload()
                    .items("transactions")
                    .mapNotNull { it.number("id")?.toInt() }

                assertEquals(ids(), ids(), "the order is not stable, so a page cannot be trusted")
            }
        }
    }

    @Test
    fun `the last posting recorded is reachable by an order the tool offers, which the date is not`() = runTest {
        crowdedMarch().use { world ->
            world.overTheProtocol { client ->
                fun firstUnder(order: String) = client
                    .callTool("list_transactions", """{"month":"2026-03","limit":1,"order_by":"$order"}""")
                    .payload()

                val byDate = firstUnder("date")
                val byRecording = firstUnder("recorded")

                assertEquals("recorded", byRecording.text("ordered_by"), "the answer says how it was cut")
                assertEquals(
                    "2026-03-13",
                    byDate.items("transactions").single().text("date"),
                    "the latest date is the invoice payment",
                )
                assertEquals(
                    LAST_RECORDED,
                    byRecording.items("transactions").single().number("id")?.toInt(),
                    "the last posting entered is the sixtieth of the ones sharing the 7th",
                )
                assertNotEquals(
                    byDate.items("transactions").single().number("id"),
                    byRecording.items("transactions").single().number("id"),
                    "the fixture no longer separates the two orders, so this proves nothing",
                )
            }
        }
    }

    // ----------------------------------------------------------------------------------
    // The perspective decides the vocabulary
    // ----------------------------------------------------------------------------------

    @Test
    fun `with no account named a transfer is a transfer, and carries no direction at all`() = runTest {
        crowdedMarch().use { world ->
            world.overTheProtocol { client ->
                val transfer = client
                    .callTool("list_transactions", """{"month":"2026-03","nature":"transfer"}""")
                    .payload()
                    .items("transactions")
                    .single()

                assertEquals("transfer", transfer.text("nature"))
                assertNull(
                    transfer["direction"],
                    "there is no account to see the movement from, and answering with the " +
                        "direction of whichever leg was read would state as a property of the " +
                        "posting something only true of one end of it",
                )
                assertEquals(TRANSFERRED, transfer.at("amount").amount(), "a magnitude, with no sign")
            }
        }
    }

    /**
     * **The one filter with no aggregate behind it, and the declaration that stands in for one.**
     *
     * The ledger has no total cut by nature — there is no "total of transfers" — and inventing one
     * by summing the page is what the surface forbids everywhere else. So `nature` narrows the list
     * and leaves the totals where they are, and the payload has to say so: an argument that moves
     * the list without moving the figure beside it reads, otherwise, as the total of what is next
     * to it. This is the whole of that mitigation, and it was the part nothing exercised.
     */
    @Test
    fun `narrowing by nature leaves the totals where they are, and the payload says which arguments they reflect`() = runTest {
        crowdedMarch().use { world ->
            world.overTheProtocol { client ->
                val answer = client
                    .callTool("list_transactions", """{"month":"2026-03","nature":"transfer"}""")
                    .payload()

                assertEquals(
                    SPENT,
                    answer.at("totals").at("expense").amount(),
                    "the totals are the month's, read from the ledger — not the transfers listed " +
                        "beside them, and not the page",
                )

                val narrowedBy = answer.at("totals")["narrowed_by"]!!.jsonArray.joinToString()
                assertTrue(
                    "nature" !in narrowedBy,
                    "`nature` narrowed the list without moving the totals, so naming it among the " +
                        "arguments they reflect would be the false half of an honest payload",
                )
                assertTrue("month" in narrowedBy, "and the argument they do reflect is named")

                assertTrue(
                    answer.at("perimeter")["excludes"]!!.jsonArray.any { "nature" in it.toString() },
                    "the perimeter has to spell out that nothing else was cut the same way",
                )
            }
        }
    }

    @Test
    fun `read from either end the same transfer keeps its nature and gains opposite directions`() = runTest {
        crowdedMarch().use { world ->
            world.overTheProtocol { client ->
                fun transferSeenFrom(accountId: Int) = client
                    .callTool(
                        "list_transactions",
                        """{"month":"2026-03","account_id":$accountId,"nature":"transfer"}""",
                    )
                    .payload()
                    .items("transactions")
                    .single()

                val fromChecking = transferSeenFrom(1)
                val fromSavings = transferSeenFrom(2)

                assertEquals(
                    listOf("transfer", "transfer"),
                    listOf(fromChecking.text("nature"), fromSavings.text("nature")),
                    "a transfer is a transfer from anywhere: the nature is the ledger's, and it " +
                        "never becomes an expense because an account was named",
                )
                assertEquals("expense", fromChecking.text("direction"), "money left this one")
                assertEquals("income", fromSavings.text("direction"), "and arrived in that one")
                assertEquals(-TRANSFERRED, fromChecking.at("amount").amount())
                assertEquals(TRANSFERRED, fromSavings.at("amount").amount())
                assertEquals("Nubank", fromChecking.text("account"))
                assertEquals("Poupança", fromSavings.text("account"))
            }
        }
    }

    @Test
    fun `the expense listing holds no transfer and no invoice payment`() = runTest {
        crowdedMarch().use { world ->
            world.overTheProtocol { client ->
                val natures = client
                    .callTool("list_transactions", """{"month":"2026-03","limit":200}""")
                    .payload()
                    .items("transactions")
                    .groupingBy { it.text("nature") }
                    .eachCount()

                assertEquals(
                    mapOf<String?, Int>(
                        "income" to 1,
                        "expense" to 62,
                        "transfer" to 1,
                        "payment" to 1,
                    ),
                    natures,
                    "the natures are the ledger's own derivation, so the transfer and the card " +
                        "payment are neither expenses nor income",
                )
            }
        }
    }

    // ----------------------------------------------------------------------------------
    // The counters describe the answer, not the cut it was taken from
    // ----------------------------------------------------------------------------------

    /**
     * **`returned` is the count of what came back, and a row the mapper drops is not in it.**
     *
     * The mapper declares `null` as a legitimate answer — "a caller drops the item instead of
     * failing on a read" — so the page and the answer are two lists, and only one of them is what
     * the agent got. Counting the first and sending the second is a disagreement nothing in the
     * payload denounces: both numbers look consistent, and the arithmetic an agent pages by is
     * defined over the count rather than over the array.
     */
    @Test
    fun `a row the mapper cannot read is dropped, and the count is of what came back`() = runTest {
        marchWithARowNobodyCanRead().use { world ->
            world.overTheProtocol { client ->
                val payload = client.callTool("list_transactions", MARCH).payload()

                assertEquals(
                    WITH_THE_UNREADABLE_ROW,
                    payload.number("matching")?.toInt(),
                    "the filter reaches the row too — it is the answer it cannot carry",
                )
                assertEquals(
                    READABLE,
                    payload.items("transactions").size,
                    "the fixture no longer holds a row the mapper drops, so this proves nothing",
                )
                assertEquals(
                    payload.items("transactions").size,
                    payload.number("returned")?.toInt(),
                    "`returned` was taken from the page before the drop, so the agent is told it " +
                        "received a posting the answer does not carry",
                )
            }
        }
    }

    // ----------------------------------------------------------------------------------

    /**
     * March as the questions family knows it, plus a posting **no surface can read**: a correction
     * between two nominal legs, with nothing on an account money sits in.
     *
     * `Transaction.primaryEntry` is `null` for it, so `legUnder(null)` is too and the mapper drops
     * it. No write path in the app produces such a row today — which is exactly why it is seeded
     * leg by leg, as the ledger's own suites do: the count and the list can only be told apart by a
     * page that loses something between the cut and the answer.
     */
    private suspend fun marchWithARowNobodyCanRead(): AgentWorld {
        val world = AgentWorld()
        world.seedMarch()
        world.posting(
            "2026-03-09",
            (MarchWorld.NOMINAL_EXPENSE posts RECLASSIFIED_CENTS)
                .taggedWith(MarchWorld.DIMENSION_GROCERIES),
            MarchWorld.NOMINAL_EXPENSE posts -RECLASSIFIED_CENTS,
            title = "Reclassificação",
        )
        return world
    }

    /**
     * March as the questions family knows it, plus sixty small grocery runs **all on the same day**
     * and all recorded after everything else.
     *
     * Both facts are the point. Sixty postings sharing a date is what an order by date alone cannot
     * settle; sixty recorded last is what makes "the last thing I entered" a different answer from
     * "the latest date".
     */
    private suspend fun crowdedMarch(): AgentWorld {
        val world = AgentWorld()
        world.seedMarch()
        repeat(EXTRA_POSTINGS) {
            world.posting(
                "2026-03-07",
                MarchWorld.ACCOUNT_CHECKING posts -EXTRA_CENTS,
                (MarchWorld.NOMINAL_EXPENSE posts EXTRA_CENTS)
                    .taggedWith(MarchWorld.DIMENSION_GROCERIES),
                title = "Padaria",
            )
        }
        return world
    }

    private fun JsonObject.items(field: String): List<JsonObject> =
        this[field]!!.jsonArray.map { it.jsonObject }

    private companion object {

        const val MARCH = """{"month":"2026-03"}"""

        const val EXTRA_POSTINGS = 60
        const val EXTRA_CENTS = 1_000L
        const val EXTRA_TOTAL = 600.00

        /** The five of `seedMarch`, plus the sixty. */
        const val POSTINGS = 65
        const val LAST_RECORDED = POSTINGS

        /** The five of `seedMarch`, and the sixth the mapper cannot read. */
        const val WITH_THE_UNREADABLE_ROW = 6
        const val READABLE = 5
        const val RECLASSIFIED_CENTS = 1_000L

        /** What `list_transactions` returns when nobody asks for a size. */
        const val PAGE = 50
        const val STEP = 20

        const val SALARY = MarchWorld.SALARY
        const val TRANSFERRED = MarchWorld.TRANSFERRED
        const val SPENT = MarchWorld.SPENT + EXTRA_TOTAL

        /**
         * What the first page's own expense lines add up to: the card purchase, plus the
         * forty-seven small ones that fit after the three postings dated later than them.
         */
        const val SPENT_ON_THE_FIRST_PAGE = MarchWorld.GROCERIES_ON_CARD + 47 * 10.00

        /**
         * Seen from the checking account: the groceries, the sixty small ones, the transfer out and
         * the invoice payment — every posting that crossed its boundary outwards.
         */
        const val LEFT_THE_CHECKING_ACCOUNT =
            MarchWorld.GROCERIES_FROM_ACCOUNT + EXTRA_TOTAL + MarchWorld.TRANSFERRED + MarchWorld.INVOICE_PAID
    }
}
