package com.neoutils.finsight.mcp

import com.neoutils.finsight.database.entity.AccountEntity
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **A figure that spans currencies arrives decomposed, consolidated and dated.**
 *
 * The ledger answers per currency because reais and dollars do not add. Everything downstream of
 * that has exactly one job — to not throw it away. A payload of `{"amount": 2000}` over a thousand
 * reais and two hundred dollars is not wrong by a rounding: it is a number the agent will report as
 * a fact, with nothing anywhere to contradict it, and no way for anyone to find out which rate
 * produced it or whether one was applied at all.
 *
 * The case the local rate archive makes unavoidable is the second one here: a currency with no rate
 * on file is a state the real flow *requires* — a user creates their first foreign account and is in
 * it immediately — so the answer says what it could not do instead of dropping the currency or
 * passing an approximation off as exact (design D16).
 */
class CrossCurrencyFigureTest {

    @Test
    fun `a balance spanning two currencies carries the parts, the total and the rate's date`() = runTest {
        twoCurrencies(rates = mapOf(USD to 5.0)).use { world ->
            world.overTheProtocol { client ->
                val figure = client.callTool("get_balance", MARCH).payload().at("balance")

                assertEquals(
                    mapOf(BRL to 1_000.00, USD to 200.00),
                    figure.byCurrency(),
                    "the decomposition is the ledger's own answer, exact in each currency",
                )
                assertEquals(2_000.00, figure.amount(), "a thousand reais and two hundred dollars at 5,00")
                assertEquals(BRL, figure.currency())
                assertEquals(true, figure.flag("is_approximate"), "a rate multiplied something")
                assertEquals("2026-03-31", figure.text("rate_date"))
                assertNull(figure["limitation"], "every part converted; there was nothing to explain")
            }
        }
    }

    @Test
    fun `net worth spanning two currencies is decomposed the same way`() = runTest {
        twoCurrencies(rates = mapOf(USD to 5.0)).use { world ->
            world.overTheProtocol { client ->
                val figure = client.callTool("get_net_worth").payload().at("net_worth")

                assertEquals(mapOf(BRL to 1_000.00, USD to 200.00), figure.byCurrency())
                assertEquals(2_000.00, figure.amount())
                assertEquals(BRL, figure.currency())
                assertNotNull(figure.text("rate_date"), "a consolidated figure has to say when")
            }
        }
    }

    /**
     * With no rate on file, there is no single number — and saying so is the answer.
     *
     * Neither alternative is acceptable: dropping the dollars would report a smaller total as if it
     * were the total, and picking one of the two currencies would be choosing a denomination by hand.
     */
    @Test
    fun `a currency no rate reaches is named, and the number says what it leaves out`() = runTest {
        twoCurrencies(rates = emptyMap()).use { world ->
            world.overTheProtocol { client ->
                val figure = client.callTool("get_balance", MARCH).payload().at("balance")

                assertEquals(
                    mapOf(BRL to 1_000.00, USD to 200.00),
                    figure.byCurrency(),
                    "the parts are exact whatever the rates could not do",
                )
                assertEquals(
                    1_000.00,
                    figure.amount(),
                    "the part that could be expressed in the base — never the dollars dropped in " +
                        "silence, and never a number the two were forced into",
                )
                assertEquals(BRL, figure.currency())
                assertNull(figure.text("rate_date"), "no rate was applied, so naming one names a fiction")

                val limitation = figure.at("limitation")
                assertEquals(
                    listOf(USD),
                    limitation["missing_rate_for"]!!.jsonArray.map { it.toString().trim('"') },
                )
                assertTrue(
                    USD in limitation.text("explanation").orEmpty() &&
                        "by_currency" in limitation.text("explanation").orEmpty(),
                    "the limitation does not say what is missing nor where to find it: $limitation",
                )
            }
        }
    }

    /**
     * With no rate reaching **either** currency there is no number at all, and the figure says so
     * rather than promoting one of the two terms — which would be choosing a denomination by hand.
     */
    @Test
    fun `a figure no rate reaches at all has no number, and does not invent one`() = runTest {
        twoCurrencies(rates = emptyMap(), base = "EUR").use { world ->
            world.overTheProtocol { client ->
                val figure = client.callTool("get_balance", MARCH).payload().at("balance")

                assertNull(figure.amount(), "naming one of the two as the figure would pick a currency")
                assertNull(figure.currency())
                assertEquals(mapOf(BRL to 1_000.00, USD to 200.00), figure.byCurrency())
                assertEquals(
                    listOf(BRL, USD),
                    figure.at("limitation")["missing_rate_for"]!!.jsonArray
                        .map { it.toString().trim('"') },
                )
            }
        }
    }

    /** A figure that never left one currency is exact, in that currency, whatever the base is. */
    @Test
    fun `a balance scoped to one account stays in that account's currency and carries no mark`() = runTest {
        twoCurrencies(rates = mapOf(USD to 5.0)).use { world ->
            world.overTheProtocol { client ->
                val figure = client
                    .callTool("get_balance", """{"month":"2026-03","account_id":3}""")
                    .payload()
                    .at("balance")

                assertEquals(200.00, figure.amount())
                assertEquals(USD, figure.currency())
                assertEquals(mapOf(USD to 200.00), figure.byCurrency())
                assertEquals(false, figure.flag("is_approximate"))
                assertNull(figure.text("rate_date"))
            }
        }
    }

    // ----------------------------------------------------------------------------------

    /** A thousand reais in one account and two hundred dollars in another. */
    private suspend fun twoCurrencies(
        rates: Map<String, Double>,
        base: String = BRL,
    ): AgentWorld {
        val world = AgentWorld(
            baseCurrency = base,
            rates = rates,
            currenciesInUse = listOf(BRL, USD),
        )
        world.account(1, "Nubank", currency = BRL)
        world.account(3, "Wise", currency = USD)
        world.ledgerAccount(200, AccountEntity.Type.INCOME, "Receitas", currency = BRL)
        world.ledgerAccount(201, AccountEntity.Type.INCOME, "Income", currency = USD)

        world.posting("2026-03-05", 1L posts 100_000, 200L posts -100_000)
        world.posting(
            "2026-03-06",
            (3L posts 20_000) inCurrency USD,
            (201L posts -20_000) inCurrency USD,
        )
        return world
    }

    private companion object {
        const val BRL = "BRL"
        const val USD = "USD"
        const val MARCH = """{"month":"2026-03"}"""
    }
}
