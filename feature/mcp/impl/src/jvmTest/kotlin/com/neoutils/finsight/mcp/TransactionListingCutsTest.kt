package com.neoutils.finsight.mcp

import com.neoutils.finsight.database.entity.AccountEntity
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.YearMonth
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **The four cuts `list_transactions` is asked for, over a world that can tell them apart.**
 *
 * A filter is only exercised by a fixture that holds something it must leave out. A month asked of a
 * world whose every posting is in that month is a filter with nothing to filter — it can be deleted
 * outright and every assertion still passes — and the same is true of a category nothing is outside
 * of and a card nothing is off. So this world spans four months either side of the boundary, splits
 * its March between two categories, and puts one of its postings on a card:
 *
 * - **the month**, on the two days that decide it — the first and the last — with a posting on the
 *   day before and the day after that must not appear, and the neighbouring months asked for in turn
 *   so that an off-by-one at either end has to move a posting between two answers;
 * - **the empty month**, which is an answer and not a failure: nothing matched, nothing came back,
 *   and the totals are still figures;
 * - **the card**, which is not an account and reaches the ledger only through the `LIABILITY` row it
 *   projects onto — the one translation nothing else in this suite performs;
 * - **the category**, whose cut belongs to the domain's own `SpendingSubject` and whose totals are
 *   the ledger's for that dimension, not the sum of the rows beside them.
 */
class TransactionListingCutsTest {

    // ----------------------------------------------------------------------------------
    // The month, on the days that decide it
    // ----------------------------------------------------------------------------------

    @Test
    fun `the month holds its first and its last day, and neither neighbour`() = runTest {
        fourMonths().use { world ->
            world.overTheProtocol { client ->
                val payload = client.callTool("list_transactions", MARCH).payload()

                assertEquals(POSTINGS_IN_MARCH, payload.number("matching")?.toInt())
                assertEquals(
                    setOf("2026-03-01", "2026-03-15", "2026-03-31"),
                    payload.items("transactions").mapNotNull { it.text("date") }.toSet(),
                    "the first and the last day of the month are inside it, and the posting of " +
                        "the 28th of February and the one of the 1st of April are not",
                )
                assertEquals(
                    SPENT_IN_MARCH,
                    payload.at("totals", "expense").amount(),
                    "the ledger's own figure for the same month, which no posting outside it reaches",
                )
            }
        }
    }

    /**
     * The same world read a month either side.
     *
     * It is what an off-by-one cannot survive: a boundary that slips by a day does not empty an
     * answer, it **moves a posting from one month's answer into another's**, and only asking for
     * both months makes the two halves of that visible.
     */
    @Test
    fun `each neighbouring month answers with its own posting and never with March's`() = runTest {
        fourMonths().use { world ->
            world.overTheProtocol { client ->
                fun monthOf(month: String) = client
                    .callTool("list_transactions", """{"month":"$month"}""")
                    .payload()

                val february = monthOf("2026-02")
                assertEquals(1, february.number("matching")?.toInt())
                assertEquals("2026-02-28", february.items("transactions").single().text("date"))
                assertEquals(SPENT_IN_FEBRUARY, february.at("totals", "expense").amount())

                val april = monthOf("2026-04")
                assertEquals(1, april.number("matching")?.toInt())
                assertEquals("2026-04-01", april.items("transactions").single().text("date"))
                assertEquals(SPENT_IN_APRIL, april.at("totals", "expense").amount())
            }
        }
    }

    // ----------------------------------------------------------------------------------
    // A month with nothing in it
    // ----------------------------------------------------------------------------------

    /**
     * A quiet month is an **answer**. An agent that receives an error for one has been told the
     * question failed, and will ask it again some other way rather than report that nothing happened.
     */
    @Test
    fun `a month the ledger holds nothing in is answered emptily rather than failing`() = runTest {
        fourMonths().use { world ->
            world.overTheProtocol { client ->
                val payload = client
                    .callTool("list_transactions", """{"month":"2026-01"}""")
                    .payload()

                assertEquals("2026-01", payload.at("period").text("month"))
                assertEquals(0, payload.number("matching")?.toInt())
                assertEquals(0, payload.number("returned")?.toInt())
                assertEquals(false, payload.flag("has_more"), "there is no page after nothing")
                assertEquals(emptyList(), payload.items("transactions"))

                assertEquals(
                    0.0,
                    payload.at("totals", "expense").amount(),
                    "a total of nothing is still a figure, and a figure still has a number",
                )
                assertEquals(0.0, payload.at("totals", "income").amount())
                assertEquals(
                    AgentWorld.BRL,
                    payload.at("totals", "expense").currency(),
                    "and it is denominated in the currency this user holds",
                )
            }
        }
    }

    // ----------------------------------------------------------------------------------
    // The card, which reaches the ledger as its liability account
    // ----------------------------------------------------------------------------------

    /**
     * **A card is a facade, and the ledger has never heard of it.** "A posting of card X" is "a
     * posting with a leg on X's `LIABILITY` account", and the tool is the one place that translation
     * happens. Handing the ledger the card's own identity instead would silently read some *other*
     * account's month — the account that happens to carry that number — and answer it under the
     * card's name.
     */
    @Test
    fun `a listing read from a card is the card's own postings, seen from it`() = runTest {
        fourMonths().use { world ->
            world.overTheProtocol { client ->
                val payload = client
                    .callTool("list_transactions", """{"month":"2026-03","card_id":$CARD}""")
                    .payload()

                assertEquals("Cartão", payload.text("read_from"))
                assertEquals(
                    1,
                    payload.number("matching")?.toInt(),
                    "one of March's three postings is on the card, and it is the only one",
                )

                val posting = payload.items("transactions").single()
                assertEquals("2026-03-31", posting.text("date"))
                assertEquals("Cartão", posting.text("account"))
                assertEquals(
                    ACCOUNT_CARD,
                    posting.number("account_id")?.toLong(),
                    "the card entered as its liability account, and the row is read through that leg",
                )
                assertEquals(true, posting.flag("is_on_card"))
                assertEquals("expense", posting.text("direction"), "it was charged to the card")
                assertEquals(ON_THE_CARD, posting.at("amount").amount())

                assertEquals(
                    ON_THE_CARD,
                    payload.at("totals", "expense").amount(),
                    "the ledger's figure for this card over the month, not the sum of the page",
                )
                assertTrue(
                    "account_id or card_id" in payload.at("totals").toString(),
                    "the totals do not say they are narrowed by the card: ${payload.at("totals")}",
                )
            }
        }
    }

    @Test
    fun `a card_id that matches nothing is refused by name rather than answered emptily`() = runTest {
        fourMonths().use { world ->
            world.overTheProtocol { client ->
                val response = client
                    .callTool("list_transactions", """{"month":"2026-03","card_id":404}""")

                assertTrue(response.isToolError(), "the refusal did not arrive flagged as an error")

                val reason = response.payload().text("reason").orEmpty()
                assertTrue(
                    "404" in reason && "credit card" in reason,
                    "the refusal does not say what was not found: ${response.toolText()}",
                )
            }
        }
    }

    // ----------------------------------------------------------------------------------
    // The category, cut by the domain's own rule
    // ----------------------------------------------------------------------------------

    @Test
    fun `narrowing by category keeps the postings classified under it, and the totals follow`() = runTest {
        fourMonths().use { world ->
            world.overTheProtocol { client ->
                fun under(categoryId: Long) = client
                    .callTool("list_transactions", """{"month":"2026-03","category_id":$categoryId}""")
                    .payload()

                val transport = under(CATEGORY_TRANSPORT)
                assertEquals(2, transport.number("matching")?.toInt())
                assertEquals(
                    setOf("2026-03-15", "2026-03-31"),
                    transport.items("transactions").mapNotNull { it.text("date") }.toSet(),
                    "the two March postings classified under it — one from the account, one from " +
                        "the card — and neither of the ones that are not",
                )
                assertTrue(
                    transport.items("transactions").all { it.text("category") == "Transporte" },
                )
                assertEquals(
                    SPENT_ON_TRANSPORT,
                    transport.at("totals", "expense").amount(),
                    "the ledger's total for the dimension, over the same month",
                )
                assertEquals(
                    0.0,
                    transport.at("totals", "income").amount(),
                    "an expense category moves no income, and the figure says so rather than " +
                        "repeating the month's",
                )
                assertTrue(
                    "category_id" in transport.at("totals")["narrowed_by"]!!.jsonArray.joinToString(),
                    "the totals do not name the argument they were narrowed by: " +
                        "${transport.at("totals")}",
                )

                val groceries = under(CATEGORY_GROCERIES)
                assertEquals(
                    1,
                    groceries.number("matching")?.toInt(),
                    "the other category has one posting in March, and two outside it",
                )
                assertEquals("2026-03-01", groceries.items("transactions").single().text("date"))
                assertEquals(SPENT_ON_GROCERIES, groceries.at("totals", "expense").amount())
            }
        }
    }

    @Test
    fun `a category_id that matches nothing is refused by name rather than ignored`() = runTest {
        fourMonths().use { world ->
            world.overTheProtocol { client ->
                val response = client
                    .callTool("list_transactions", """{"month":"2026-03","category_id":404}""")

                assertTrue(response.isToolError(), "the refusal did not arrive flagged as an error")

                val reason = response.payload().text("reason").orEmpty()
                assertTrue(
                    "404" in reason && "category" in reason,
                    "the refusal does not say what was not found: ${response.toolText()}",
                )
            }
        }
    }

    // ----------------------------------------------------------------------------------

    /**
     * Four months of one user's life, arranged so that every cut this class asks for has something
     * to leave out.
     *
     * The five postings are placed rather than accumulated: one on the last day of February and one
     * on the first of April, which bracket the month under test; one on each boundary day of March
     * itself; two categories split across the three months, so neither is the whole of any of them;
     * and one posting on the card, dated the last day of March, so that the card's perspective and
     * the month's far edge are exercised by the same row.
     */
    private suspend fun fourMonths(): AgentWorld {
        val world = AgentWorld()

        world.account(ACCOUNT_CHECKING, "Nubank", isDefault = true)
        val card = world.card(
            id = CARD,
            accountId = ACCOUNT_CARD,
            name = "Cartão",
            limit = CARD_LIMIT,
        )

        world.ledgerAccount(NOMINAL_EXPENSE, AccountEntity.Type.EXPENSE, "Despesas")

        world.category(id = CATEGORY_GROCERIES, dimensionId = DIMENSION_GROCERIES, name = "Mercado")
        world.category(id = CATEGORY_TRANSPORT, dimensionId = DIMENSION_TRANSPORT, name = "Transporte")
        world.invoice(id = 1, dimensionId = DIMENSION_INVOICE, card = card, month = YearMonth(2026, 3))

        world.fromTheAccount("2026-02-28", cents = 10_000, dimension = DIMENSION_GROCERIES)
        world.fromTheAccount("2026-03-01", cents = 20_000, dimension = DIMENSION_GROCERIES)
        world.fromTheAccount("2026-03-15", cents = 30_000, dimension = DIMENSION_TRANSPORT)
        world.onTheCard("2026-03-31", cents = 40_000, dimension = DIMENSION_TRANSPORT)
        world.fromTheAccount("2026-04-01", cents = 50_000, dimension = DIMENSION_GROCERIES)

        return world
    }

    /** Money leaving the checking account, classified. */
    private suspend fun AgentWorld.fromTheAccount(date: String, cents: Long, dimension: Long) = posting(
        date,
        ACCOUNT_CHECKING posts -cents,
        (NOMINAL_EXPENSE posts cents).taggedWith(dimension),
    )

    /** The same purchase charged to the card: a liability leg, and no account leg at all. */
    private suspend fun AgentWorld.onTheCard(date: String, cents: Long, dimension: Long) = posting(
        date,
        (ACCOUNT_CARD posts -cents).taggedWith(DIMENSION_INVOICE),
        (NOMINAL_EXPENSE posts cents).taggedWith(dimension),
    )

    private fun JsonObject.items(field: String): List<JsonObject> =
        this[field]!!.jsonArray.map { it.jsonObject }

    private companion object {

        const val MARCH = """{"month":"2026-03"}"""

        const val ACCOUNT_CHECKING = 1L
        const val ACCOUNT_CARD = 10L
        const val NOMINAL_EXPENSE = 100L

        const val CARD = 1L
        const val CARD_LIMIT = 5_000.0

        const val CATEGORY_GROCERIES = 1L
        const val CATEGORY_TRANSPORT = 2L
        const val DIMENSION_GROCERIES = 1L
        const val DIMENSION_TRANSPORT = 2L
        const val DIMENSION_INVOICE = 10L

        const val POSTINGS_IN_MARCH = 3

        /** What each posting comes to, in the major unit, in the order they are seeded. */
        const val SPENT_IN_FEBRUARY = 100.00
        const val ON_THE_FIRST = 200.00
        const val MID_MARCH = 300.00
        const val ON_THE_CARD = 400.00
        const val SPENT_IN_APRIL = 500.00

        const val SPENT_IN_MARCH = ON_THE_FIRST + MID_MARCH + ON_THE_CARD
        const val SPENT_ON_TRANSPORT = MID_MARCH + ON_THE_CARD
        const val SPENT_ON_GROCERIES = ON_THE_FIRST
    }
}
