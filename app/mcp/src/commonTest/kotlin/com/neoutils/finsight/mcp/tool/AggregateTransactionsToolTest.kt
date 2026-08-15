@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.mcp.tool

import com.neoutils.finsight.domain.model.ExchangeRate
import com.neoutils.finsight.domain.model.MoneyByCurrency
import com.neoutils.finsight.domain.repository.ScopeStatsByCurrency
import com.neoutils.finsight.mcp.FakeAccountRepository
import com.neoutils.finsight.mcp.FakeCategoryRepository
import com.neoutils.finsight.mcp.FakeCreditCardRepository
import com.neoutils.finsight.mcp.FakeEntryRepository
import com.neoutils.finsight.mcp.TEST_ZONE
import com.neoutils.finsight.mcp.account
import com.neoutils.finsight.mcp.category
import com.neoutils.finsight.mcp.clockAt
import com.neoutils.finsight.mcp.contract.ResponseLimits
import com.neoutils.finsight.mcp.contract.ToolOutcome
import com.neoutils.finsight.mcp.contract.ToolWarningCode
import com.neoutils.finsight.mcp.moneyFactory
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime

class AggregateTransactionsToolTest {

    private val today = LocalDate(2026, 7, 15)

    private val checking = account(1, "Checking", currency = "BRL")
    private val groceries = category(20, "Groceries")

    private val period = """"startDate":"2026-06-01","endDate":"2026-06-30""""

    @Test
    fun `a total is a collection of amounts even with a single currency in use`() = runTest {
        val groups = tool().groups("""{$period}""")

        val total = groups.single { it["category"] != null }["total"]!!.jsonObject
        val amounts = total["amounts"]!!.jsonArray
        assertEquals(1, amounts.size)
        assertEquals("BRL", amounts.single().jsonObject["currency"]!!.jsonPrimitive.content)
    }

    @Test
    fun `spending reads negative, because the ledger's debit-positive convention stays inside it`() = runTest {
        val groups = tool().groups("""{$period}""")

        val total = groups.single { it["category"] != null }["total"]!!.jsonObject
        assertEquals(-30_000, total["amounts"]!!.jsonArray.single().jsonObject["minorUnits"]!!.jsonPrimitive.content.toLong())
    }

    @Test
    fun `the unclassified total is a line of the same aggregate, not a category`() = runTest {
        val groups = tool().groups("""{$period}""")

        val unclassified = groups.single { it["isUncategorized"] != null }
        assertNull(unclassified["category"])
        assertEquals(-5_000, unclassified["total"]!!.jsonObject["amounts"]!!.jsonArray.single().jsonObject["minorUnits"]!!.jsonPrimitive.content.toLong())
    }

    @Test
    fun `a missing rate is a warning on a successful call, never an error and never a number`() = runTest {
        val outcome = tool(
            totals = mapOf(groceries.dimensionId to MoneyByCurrency.of(mapOf("BRL" to 300.0, "USD" to 50.0))),
        ).execute(json("""{$period}"""))

        val ok = assertIs<ToolOutcome.Ok>(outcome)
        assertEquals(ToolWarningCode.MISSING_EXCHANGE_RATE, ok.warnings.single().code)

        val total = ok.result["groups"]!!.jsonArray.single().jsonObject["total"]!!.jsonObject
        assertEquals(2, total["amounts"]!!.jsonArray.size)
        assertEquals("unavailable", total["consolidated"]!!.jsonObject["kind"]!!.jsonPrimitive.content)
    }

    @Test
    fun `a consolidated value carries the rate and the date that produced it`() = runTest {
        val outcome = tool(
            totals = mapOf(groceries.dimensionId to MoneyByCurrency.of(mapOf("BRL" to 300.0, "USD" to 50.0))),
            rates = mapOf(
                "USD" to ExchangeRate(
                    currency = "USD",
                    counterCurrency = "BRL",
                    date = LocalDate(2026, 6, 30),
                    rate = 5.0,
                    source = ExchangeRate.Source.USER,
                ),
            ),
        ).execute(json("""{$period}"""))

        val ok = assertIs<ToolOutcome.Ok>(outcome)
        val consolidated = ok.result["groups"]!!.jsonArray.single().jsonObject["total"]!!
            .jsonObject["consolidated"]!!.jsonObject

        assertEquals("available", consolidated["kind"]!!.jsonPrimitive.content)
        assertEquals("2026-06-30", consolidated["asOf"]!!.jsonPrimitive.content)
        assertEquals("USD", consolidated["appliedRates"]!!.jsonArray.single().jsonObject["currency"]!!.jsonPrimitive.content)
    }

    @Test
    fun `an aggregate does not paginate`() = runTest {
        val result = tool().run("""{$period}""")

        assertNull(result["nextCursor"])
        assertNull(result["totalMatching"])
    }

    @Test
    fun `a period is required, because an aggregate without bounds has no declared size`() = runTest {
        val failed = assertIs<ToolOutcome.Failed>(tool().execute(json("""{"endDate":"2026-06-30"}""")))
        assertEquals(AggregateCodes.PERIOD_REQUIRED, failed.error.code)
    }

    @Test
    fun `an answer past the declared size is refused with guidance, never dumped`() = runTest {
        // One line per category, over enough categories that the answer cannot fit.
        val many: Map<Long?, MoneyByCurrency> = (1..4_000L).associate { it to MoneyByCurrency.of("BRL", it.toDouble()) }
        val categories = (1..4_000L).map { category(it, "Category $it", dimensionId = it) }

        val outcome = AggregateTransactionsTool(
            entries = FakeEntryRepository(dimensionTotals = many),
            accounts = FakeAccountRepository(listOf(checking)),
            creditCards = FakeCreditCardRepository(),
            categories = FakeCategoryRepository(categories),
            money = moneyFactory(),
            clock = clockAt(today),
            timeZone = TEST_ZONE,
        ).execute(json("""{$period}"""))

        val failed = assertIs<ToolOutcome.Failed>(outcome)
        assertEquals(ResponseLimits.CODE_RESPONSE_TOO_LARGE, failed.error.code)
        assertContains(failed.error.message, "shorter period")
    }

    @Test
    fun `a month breakdown answers one line per month of the period`() = runTest {
        val groups = tool().groups("""{"startDate":"2026-05-15","endDate":"2026-07-02","groupBy":"MONTH"}""")

        assertEquals(
            listOf("2026-05", "2026-06", "2026-07"),
            groups.map { it["month"]!!.jsonPrimitive.content },
        )
    }

    @Test
    fun `an account breakdown reads income positive and expense negative`() = runTest {
        val groups = tool().groups("""{$period,"groupBy":"ACCOUNT"}""")

        val line = groups.single()
        assertEquals(1, line["account"]!!.jsonObject["id"]!!.jsonPrimitive.content.toInt())
        assertEquals(100_000, line["income"]!!.jsonObject.first())
        assertEquals(-40_000, line["expense"]!!.jsonObject.first())
    }

    @Test
    fun `the tool announces itself as read-only`() {
        assertTrue(tool().annotations.readOnlyHint)
    }

    private fun tool(
        // The ledger answers this aggregate keyed by **dimension**, and the `null` key is
        // the unclassified group of that same read.
        totals: Map<Long?, MoneyByCurrency> = mapOf(
            groceries.dimensionId to MoneyByCurrency.of("BRL", 300.0),
            null to MoneyByCurrency.of("BRL", 50.0),
        ),
        rates: Map<String, ExchangeRate> = emptyMap(),
    ) = AggregateTransactionsTool(
        entries = FakeEntryRepository(
            dimensionTotals = totals,
            scopeStats = ScopeStatsByCurrency(
                income = MoneyByCurrency.of("BRL", 1_000.0),
                expense = MoneyByCurrency.of("BRL", 400.0),
                balance = MoneyByCurrency.of("BRL", 600.0),
                openingBalance = MoneyByCurrency.of("BRL", 100.0),
            ),
        ),
        accounts = FakeAccountRepository(listOf(checking)),
        creditCards = FakeCreditCardRepository(),
        categories = FakeCategoryRepository(listOf(groceries)),
        money = moneyFactory(rates = rates, inUse = if (rates.isEmpty()) listOf("BRL") else listOf("BRL", "USD")),
        clock = clockAt(today),
        timeZone = TEST_ZONE,
    )

    private suspend fun AggregateTransactionsTool.run(arguments: String): JsonObject =
        assertIs<ToolOutcome.Ok>(execute(json(arguments))).result

    private suspend fun AggregateTransactionsTool.groups(arguments: String): List<JsonObject> =
        run(arguments)["groups"]!!.jsonArray.map { it.jsonObject }

    private fun JsonObject.first(): Long =
        this["amounts"]!!.jsonArray.single().jsonObject["minorUnits"]!!.jsonPrimitive.content.toLong()

    private fun json(raw: String): JsonObject = Json.parseToJsonElement(raw) as JsonObject
}
