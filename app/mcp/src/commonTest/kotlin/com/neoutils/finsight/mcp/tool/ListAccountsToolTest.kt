@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.mcp.tool

import com.neoutils.finsight.domain.usecase.CalculateBalanceUseCase
import com.neoutils.finsight.mcp.FakeAccountRepository
import com.neoutils.finsight.mcp.FakeEntryRepository
import com.neoutils.finsight.mcp.TEST_ZONE
import com.neoutils.finsight.mcp.account
import com.neoutils.finsight.mcp.clockAt
import com.neoutils.finsight.mcp.conversionAccount
import com.neoutils.finsight.mcp.contract.ResponseLimits
import com.neoutils.finsight.mcp.contract.ToolOutcome
import com.neoutils.finsight.mcp.expensesAccount
import com.neoutils.finsight.mcp.incomesAccount
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime

class ListAccountsToolTest {

    private val today = LocalDate(2026, 3, 15)

    private val checking = account(1, "Checking", currency = "BRL", isDefault = true)
    private val abroad = account(2, "Abroad", currency = "USD")
    private val closed = account(3, "Closed", currency = "BRL", isArchived = true)

    @Test
    fun `no system account of the ledger is ever listed`() = runTest {
        val result = tool().run()

        val names = result["accounts"]!!.jsonArray.map { it.jsonObject["name"]!!.jsonPrimitive.content }
        assertEquals(listOf("Checking", "Abroad"), names)
        listOf(expensesAccount, incomesAccount, conversionAccount).forEach { system ->
            assertTrue(system.name !in names, "`${system.name}` is mechanism, not a fact about the user")
        }
    }

    @Test
    fun `an account balance is a single amount, because an account is a single currency`() = runTest {
        val result = tool().run()

        val first = result["accounts"]!!.jsonArray.first().jsonObject
        assertEquals("BRL", first["balance"]!!.jsonObject["currency"]!!.jsonPrimitive.content)
        assertEquals("150000", first["balance"]!!.jsonObject["minorUnits"]!!.jsonPrimitive.content)
        assertEquals("2", first["balance"]!!.jsonObject["scale"]!!.jsonPrimitive.content)
    }

    @Test
    fun `archived accounts are left out and the scope is echoed`() = runTest {
        val result = tool().run()

        assertEquals(2, result["totalMatching"]!!.jsonPrimitive.content.toInt())
        val archived = result["assumed"]!!.jsonObject["archived"]!!.jsonObject
        assertEquals("EXCLUDED", archived["value"]!!.jsonPrimitive.content)
        assertEquals("true", archived["wasAssumed"]!!.jsonPrimitive.content)
    }

    @Test
    fun `asking for the archived ones says the scope was not assumed`() = runTest {
        val result = tool().run("""{"archived":"ONLY"}""")

        assertEquals(listOf("Closed"), result["accounts"]!!.jsonArray.map { it.jsonObject["name"]!!.jsonPrimitive.content })
        val archived = result["assumed"]!!.jsonObject["archived"]!!.jsonObject
        assertEquals("false", archived["wasAssumed"]!!.jsonPrimitive.content)
    }

    @Test
    fun `the reference date the server chose is echoed`() = runTest {
        val result = tool().run()

        val reference = result["assumed"]!!.jsonObject["referenceDate"]!!.jsonObject
        assertEquals(today.toString(), reference["value"]!!.jsonPrimitive.content)
        assertEquals("true", reference["wasAssumed"]!!.jsonPrimitive.content)
    }

    @Test
    fun `a page above the ceiling is refused naming it, never truncated`() = runTest {
        val outcome = tool().execute(json("""{"limit":${ResponseLimits.MAX_PAGE_SIZE + 1}}"""))

        val failed = assertIs<ToolOutcome.Failed>(outcome)
        assertEquals(ResponseLimits.CODE_PAGE_LIMIT_ABOVE_CEILING, failed.error.code)
        assertTrue(failed.error.message.contains(ResponseLimits.MAX_PAGE_SIZE.toString()))
    }

    @Test
    fun `a page resumes after the record its cursor stands for`() = runTest {
        val first = tool().run("""{"limit":1}""")
        assertEquals("Checking", first["accounts"]!!.jsonArray.single().jsonObject["name"]!!.jsonPrimitive.content)

        val cursor = first["nextCursor"]!!.jsonPrimitive.content
        val second = tool().run("""{"limit":1,"cursor":"$cursor"}""")
        assertEquals("Abroad", second["accounts"]!!.jsonArray.single().jsonObject["name"]!!.jsonPrimitive.content)
    }

    private fun tool() = ListAccountsTool(
        accounts = FakeAccountRepository(
            userAccounts = listOf(checking, abroad, closed),
            chart = listOf(checking, abroad, closed, expensesAccount, incomesAccount, conversionAccount),
        ),
        calculateBalance = CalculateBalanceUseCase(
            FakeEntryRepository(accountBalances = mapOf(1L to 1_500.0, 2L to 20.0)),
        ),
        clock = clockAt(today),
        timeZone = TEST_ZONE,
    )

    private suspend fun ListAccountsTool.run(arguments: String = "{}"): JsonObject {
        val outcome = assertIs<ToolOutcome.Ok>(execute(json(arguments)))
        return outcome.result
    }

    private fun json(raw: String): JsonObject = Json.parseToJsonElement(raw) as JsonObject
}
