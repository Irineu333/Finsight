@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.mcp

import com.neoutils.finsight.domain.usecase.CalculateAvailableLimitUseCase
import com.neoutils.finsight.domain.usecase.CalculateBalanceUseCase
import com.neoutils.finsight.domain.usecase.CalculateBudgetProgressUseCase
import com.neoutils.finsight.domain.usecase.CalculateInvoiceUseCase
import com.neoutils.finsight.domain.usecase.GetPendingRecurringUseCase
import com.neoutils.finsight.mcp.contract.ToolRegistry
import com.neoutils.finsight.mcp.prompt.RECORD_STATEMENT_PROMPT
import com.neoutils.finsight.mcp.prompt.REVIEW_MONTH_PROMPT
import com.neoutils.finsight.mcp.prompt.userFlowPrompts
import com.neoutils.finsight.mcp.resource.orientationResources
import com.neoutils.finsight.mcp.tool.AGGREGATE_TRANSACTIONS_TOOL
import com.neoutils.finsight.mcp.tool.AggregateTransactionsTool
import com.neoutils.finsight.mcp.tool.GET_OVERVIEW_TOOL
import com.neoutils.finsight.mcp.tool.GetOverviewTool
import com.neoutils.finsight.mcp.tool.LIST_ACCOUNTS_TOOL
import com.neoutils.finsight.mcp.tool.LIST_BUDGETS_TOOL
import com.neoutils.finsight.mcp.tool.LIST_CATEGORIES_TOOL
import com.neoutils.finsight.mcp.tool.LIST_INSTALLMENTS_TOOL
import com.neoutils.finsight.mcp.tool.LIST_INVOICES_TOOL
import com.neoutils.finsight.mcp.tool.LIST_RECURRING_TOOL
import com.neoutils.finsight.mcp.tool.LIST_TRANSACTIONS_TOOL
import com.neoutils.finsight.mcp.tool.ListAccountsTool
import com.neoutils.finsight.mcp.tool.ListBudgetsTool
import com.neoutils.finsight.mcp.tool.ListCategoriesTool
import com.neoutils.finsight.mcp.tool.ListInstallmentsTool
import com.neoutils.finsight.mcp.tool.ListInvoicesTool
import com.neoutils.finsight.mcp.tool.ListRecurringTool
import com.neoutils.finsight.mcp.tool.ListTransactionsTool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime

/**
 * The read surface as a connected client sees it: nine tools, three orientation documents
 * and two flows, over a real loopback socket.
 *
 * It is deliberately end to end. A registry asserted in memory says nothing about whether a
 * client can reach what it holds, and the whole point of announcing a surface is that
 * somebody on the other side receives it.
 */
class ReadSurfaceTest {

    private val today = LocalDate(2026, 7, 15)
    private val token = "read-surface-token"

    private lateinit var controller: McpServerController
    private lateinit var client: McpTestClient

    private var port: Int = 0

    /** The nine reads this delivery announces. No write tool exists yet. */
    private val expectedTools = listOf(
        AGGREGATE_TRANSACTIONS_TOOL,
        GET_OVERVIEW_TOOL,
        LIST_ACCOUNTS_TOOL,
        LIST_BUDGETS_TOOL,
        LIST_CATEGORIES_TOOL,
        LIST_INSTALLMENTS_TOOL,
        LIST_INVOICES_TOOL,
        LIST_RECURRING_TOOL,
        LIST_TRANSACTIONS_TOOL,
    ).sorted()

    @BeforeTest
    fun listen(): Unit = runBlocking {
        port = freePort()
        val checking = account(1, "Checking", currency = "BRL", isDefault = true)
        val entries = FakeEntryRepository(accountBalances = mapOf(1L to 1_500.0))
        val accounts = FakeAccountRepository(
            userAccounts = listOf(checking),
            chart = listOf(checking, expensesAccount, incomesAccount, conversionAccount),
        )
        val categories = FakeCategoryRepository(listOf(category(20, "Groceries")))
        val creditCards = FakeCreditCardRepository()
        val invoices = FakeInvoiceRepository()
        val transactions = FakeTransactionRepository()
        val clock = clockAt(today)

        val overview = GetOverviewTool(
            baseCurrency = FakeBaseCurrency(),
            accounts = accounts,
            creditCards = creditCards,
            invoices = invoices,
            entries = entries,
            calculateBalance = CalculateBalanceUseCase(entries),
            calculateInvoice = CalculateInvoiceUseCase(entries),
            calculateAvailableLimit = CalculateAvailableLimitUseCase(invoices, CalculateInvoiceUseCase(entries)),
            exchangeRates = FakeExchangeRates(),
            money = moneyFactory(),
            clock = clock,
            timeZone = TEST_ZONE,
        )
        val accountsTool = ListAccountsTool(accounts, CalculateBalanceUseCase(entries), clock, TEST_ZONE)
        val categoriesTool = ListCategoriesTool(categories, clock, TEST_ZONE)

        controller = McpServerController(
            settings = FakeMcpServerSettingsRepository(enabledSettings(port, token)),
            tools = ToolRegistry(
                listOf(
                    overview,
                    accountsTool,
                    categoriesTool,
                    ListTransactionsTool(transactions, accounts, creditCards, invoices, categories, clock, TEST_ZONE),
                    AggregateTransactionsTool(entries, accounts, creditCards, categories, moneyFactory(), clock, TEST_ZONE),
                    ListInvoicesTool(invoices, creditCards, CalculateInvoiceUseCase(entries), clock, TEST_ZONE),
                    ListBudgetsTool(
                        budgets = FakeBudgetRepository(),
                        recurring = FakeRecurringRepository(),
                        transactions = transactions,
                        calculateProgress = CalculateBudgetProgressUseCase(entries, consolidateMoney()),
                        clock = clock,
                        timeZone = TEST_ZONE,
                    ),
                    ListRecurringTool(
                        recurring = FakeRecurringRepository(),
                        occurrences = FakeRecurringOccurrenceRepository(),
                        pending = GetPendingRecurringUseCase(),
                        clock = clock,
                        timeZone = TEST_ZONE,
                    ),
                    ListInstallmentsTool(
                        installments = FakeInstallmentRepository(),
                        transactions = transactions,
                        accounts = accounts,
                        creditCards = creditCards,
                        invoices = invoices,
                        categories = categories,
                        clock = clock,
                        timeZone = TEST_ZONE,
                    ),
                ),
            ),
            resources = orientationResources(overview, accountsTool, categoriesTool),
            prompts = userFlowPrompts(),
        )
        controller.start()
        client = McpTestClient(port, token)
        withContext(Dispatchers.IO) { client.initialize() }
    }

    @AfterTest
    fun shutdown(): Unit = runBlocking { controller.stop() }

    @Test
    fun `a connected client is announced the nine reads and nothing else`() {
        val tools = call(2, "tools/list")["tools"]!!.jsonArray.map { it.jsonObject }

        assertEquals(expectedTools, tools.map { it["name"]!!.jsonPrimitive.content })
        tools.forEach { tool ->
            assertEquals(
                true,
                tool["annotations"]!!.jsonObject["readOnlyHint"]!!.jsonPrimitive.content.toBoolean(),
                "`${tool["name"]}` is announced in a delivery that has no write tool",
            )
            assertTrue(
                tool["outputSchema"] != null,
                "`${tool["name"]}` declares no output schema, so its refusals would be prose",
            )
        }
    }

    @Test
    fun `the listing of transactions names the aggregation tool as the way to a total`() {
        val tools = call(2, "tools/list")["tools"]!!.jsonArray.map { it.jsonObject }
        val listing = tools.single { it["name"]!!.jsonPrimitive.content == LIST_TRANSACTIONS_TOOL }

        assertContains(listing["description"]!!.jsonPrimitive.content, AGGREGATE_TRANSACTIONS_TOOL)
    }

    @Test
    fun `the orientation documents are fetched without any tool being called`() {
        val listed = call(3, "resources/list")["resources"]!!.jsonArray.map { it.jsonObject }

        assertEquals(
            listOf("finsight://accounts", "finsight://categories", "finsight://overview"),
            listed.map { it["uri"]!!.jsonPrimitive.content },
        )

        val document = call(4, "resources/read", """{"uri":"finsight://accounts"}""")
        val text = document["contents"]!!.jsonArray.single().jsonObject["text"]!!.jsonPrimitive.content

        assertContains(text, "Checking")
        assertContains(text, "\"isError\":false")
    }

    @Test
    fun `the overview document carries the base currency and the identifiers`() {
        val document = call(5, "resources/read", """{"uri":"finsight://overview"}""")
        val text = document["contents"]!!.jsonArray.single().jsonObject["text"]!!.jsonPrimitive.content

        assertContains(text, "\"baseCurrency\":\"BRL\"")
        assertContains(text, "\"netWorth\"")
    }

    @Test
    fun `the user's flows are offered as prompts, and they name the tools that exist`() {
        val listed = call(6, "prompts/list")["prompts"]!!.jsonArray.map { it.jsonObject }

        assertEquals(
            listOf(RECORD_STATEMENT_PROMPT, REVIEW_MONTH_PROMPT).sorted(),
            listed.map { it["name"]!!.jsonPrimitive.content },
        )

        val expanded = call(
            id = 7,
            method = "prompts/get",
            params = """{"name":"$REVIEW_MONTH_PROMPT","arguments":{"startDate":"2026-06-01","endDate":"2026-06-30"}}""",
        )
        val text = expanded["messages"]!!.jsonArray.single().jsonObject["content"]!!
            .jsonObject["text"]!!.jsonPrimitive.content

        assertContains(text, AGGREGATE_TRANSACTIONS_TOOL)
        assertContains(text, "2026-06-01..2026-06-30")
    }

    @Test
    fun `a read answers with structured content under the schema it declared`() {
        val result = call(8, "tools/call", """{"name":"$LIST_ACCOUNTS_TOOL","arguments":{}}""")
        val structured = result["structuredContent"]!!.jsonObject

        assertEquals(false, structured["isError"]!!.jsonPrimitive.content.toBoolean())
        val accounts = structured["result"]!!.jsonObject["accounts"]!!.jsonArray
        assertEquals("Checking", accounts.single().jsonObject["name"]!!.jsonPrimitive.content)
        assertEquals(today.toString(), structured["result"]!!.jsonObject["assumed"]!!.jsonObject["referenceDate"]!!.jsonObject["value"]!!.jsonPrimitive.content)
    }

    private fun call(id: Int, method: String, params: String? = null) =
        client.post(request(id, method, params)).body().asJson()["result"]!!.jsonObject
}
