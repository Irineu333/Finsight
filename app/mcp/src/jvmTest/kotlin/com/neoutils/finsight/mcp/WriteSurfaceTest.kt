@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.mcp

import arrow.core.Either
import com.neoutils.finsight.domain.usecase.AdjustBalanceUseCase
import com.neoutils.finsight.domain.usecase.AdjustInvoiceUseCase
import com.neoutils.finsight.domain.usecase.CalculateAvailableLimitUseCase
import com.neoutils.finsight.domain.usecase.CalculateBalanceUseCase
import com.neoutils.finsight.domain.usecase.CalculateBudgetProgressUseCase
import com.neoutils.finsight.domain.usecase.CalculateInvoiceUseCase
import com.neoutils.finsight.domain.usecase.GetPendingRecurringUseCase
import com.neoutils.finsight.domain.usecase.HarvestExchangeRateUseCase
import com.neoutils.finsight.domain.usecase.TransferBetweenAccountsUseCase
import com.neoutils.finsight.feature.mcp.api.McpPermission
import com.neoutils.finsight.mcp.contract.McpTool
import com.neoutils.finsight.mcp.contract.ToolRegistry
import com.neoutils.finsight.mcp.tool.AGGREGATE_TRANSACTIONS_TOOL
import com.neoutils.finsight.mcp.tool.AggregateTransactionsTool
import com.neoutils.finsight.mcp.tool.DELETE_TRANSACTIONS_TOOL
import com.neoutils.finsight.mcp.tool.DeleteTransactionsTool
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
import com.neoutils.finsight.mcp.tool.PREVIEW_TRANSACTIONS_TOOL
import com.neoutils.finsight.mcp.tool.PreviewTransactionsTool
import com.neoutils.finsight.mcp.tool.ProbableDuplicates
import com.neoutils.finsight.mcp.tool.RECORD_TRANSACTIONS_TOOL
import com.neoutils.finsight.mcp.tool.RecordTransactionsTool
import com.neoutils.finsight.mcp.tool.UPDATE_TRANSACTIONS_TOOL
import com.neoutils.finsight.mcp.tool.UpdateTransactionsTool
import com.neoutils.finsight.mcp.write.ActivityRecorder
import com.neoutils.finsight.mcp.write.IdempotencyStore
import com.neoutils.finsight.mcp.write.TransactionItemResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import com.neoutils.finsight.mcp.server.DeclaredClientName

/**
 * The whole surface as a connected client sees it, over a real loopback socket: **thirteen
 * tools** at read and write, and not one of the three writes at read-only.
 *
 * A registry asserted in memory would say nothing about either. What a client is announced
 * comes off the wire, and so does the refusal a client that ignores the announcement earns.
 */
class WriteSurfaceTest {

    private val today = LocalDate(2026, 7, 15)
    private val token = "write-surface-token"

    private val checking = account(1, "Checking", currency = "BRL", isDefault = true)
    private val groceries = category(20, "Groceries")

    private val existing = transaction(
        id = 77,
        date = LocalDate(2026, 7, 1),
        entries = listOf(entry(checking, -1_000), entry(expensesAccount, 1_000, groceries.dimensionId)),
        title = "Market",
    )

    /** The nine reads and the four the write surface adds — the whole announced set. */
    private val expectedTools = listOf(
        AGGREGATE_TRANSACTIONS_TOOL,
        DELETE_TRANSACTIONS_TOOL,
        GET_OVERVIEW_TOOL,
        LIST_ACCOUNTS_TOOL,
        LIST_BUDGETS_TOOL,
        LIST_CATEGORIES_TOOL,
        LIST_INSTALLMENTS_TOOL,
        LIST_INVOICES_TOOL,
        LIST_RECURRING_TOOL,
        LIST_TRANSACTIONS_TOOL,
        PREVIEW_TRANSACTIONS_TOOL,
        RECORD_TRANSACTIONS_TOOL,
        UPDATE_TRANSACTIONS_TOOL,
    ).sorted()

    private val writeTools = listOf(
        DELETE_TRANSACTIONS_TOOL,
        RECORD_TRANSACTIONS_TOOL,
        UPDATE_TRANSACTIONS_TOOL,
    )

    private var controller: McpServerController? = null

    @AfterTest
    fun shutdown(): Unit = runBlocking { controller?.stop() }

    @Test
    fun `at read and write a client is announced thirteen tools, the three writes and the dry run among them`() {
        val client = listening(McpPermission.READ_WRITE)
        val tools = client.call(2, "tools/list")["tools"]!!.jsonArray.map { it.jsonObject }

        assertEquals(13, tools.size)
        assertEquals(expectedTools, tools.map { it["name"]!!.jsonPrimitive.content })

        val annotations = tools.associate {
            it["name"]!!.jsonPrimitive.content to it["annotations"]!!.jsonObject
        }
        writeTools.forEach { name ->
            assertEquals(
                false,
                annotations.getValue(name)["readOnlyHint"]!!.jsonPrimitive.content.toBoolean(),
                "`$name` writes and is announced as read-only",
            )
        }
        assertEquals(
            true,
            annotations.getValue(DELETE_TRANSACTIONS_TOOL)["destructiveHint"]!!.jsonPrimitive.content.toBoolean(),
        )
        assertEquals(
            true,
            annotations.getValue(RECORD_TRANSACTIONS_TOOL)["idempotentHint"]!!.jsonPrimitive.content.toBoolean(),
        )
        // The dry run is a tool of its own precisely so that it can say this truthfully.
        assertEquals(
            true,
            annotations.getValue(PREVIEW_TRANSACTIONS_TOOL)["readOnlyHint"]!!.jsonPrimitive.content.toBoolean(),
        )
    }

    @Test
    fun `at read-only not one of the three writes is announced`() {
        val client = listening(McpPermission.READ_ONLY)
        val announced = client.call(2, "tools/list")["tools"]!!.jsonArray
            .map { it.jsonObject["name"]!!.jsonPrimitive.content }

        writeTools.forEach { name ->
            assertFalse(name in announced, "`$name` writes and was announced at read-only")
        }
        assertEquals(expectedTools - writeTools.toSet(), announced)
    }

    @Test
    fun `at read-only a client that ignores the listing and calls a write is refused all the same`() {
        val client = listening(McpPermission.READ_ONLY)
        val result = client.call(
            id = 3,
            method = "tools/call",
            params = """
                {"name":"$RECORD_TRANSACTIONS_TOOL","arguments":{"items":[
                  {"intent":"EXPENSE","date":"2026-07-02","accountId":1,"amount":{"currency":"BRL","minorUnits":1000}}
                ]}}
            """.trimIndent(),
        )
        val structured = result["structuredContent"]!!.jsonObject

        assertEquals(true, structured["isError"]!!.jsonPrimitive.content.toBoolean())
        val error = structured["error"]!!.jsonObject
        assertEquals("PERMISSION", error["category"]!!.jsonPrimitive.content)
        assertEquals(false, error["isRetryable"]!!.jsonPrimitive.content.toBoolean())
        assertTrue(written.isEmpty(), "A write ran at read-only")
    }

    @Test
    fun `at read and write the dry run answers what the write would do, and the write then does it`() {
        val client = listening(McpPermission.READ_WRITE)
        val items = """
            [{"intent":"EXPENSE","date":"2026-07-02","accountId":1,"categoryId":20,
              "description":"Market","amount":{"currency":"BRL","minorUnits":1000}}]
        """.trimIndent()

        val previewed = client.call(3, "tools/call", """{"name":"$PREVIEW_TRANSACTIONS_TOOL","arguments":{"items":$items}}""")
            .structuredResult()
        assertEquals("WOULD_BE_RECORDED", previewed["items"]!!.jsonArray.single().jsonObject["status"]!!.jsonPrimitive.content)
        assertTrue(written.isEmpty(), "The dry run wrote")

        val recorded = client.call(
            id = 4,
            method = "tools/call",
            params = """{"name":"$RECORD_TRANSACTIONS_TOOL","arguments":{"items":$items,"idempotencyKey":"k-1"}}""",
        ).structuredResult()

        assertEquals(1, recorded["appliedCount"]!!.jsonPrimitive.content.toInt())
        assertEquals(1, written.size)
        assertContains(journal.records.single().tool, RECORD_TRANSACTIONS_TOOL)

        // The client is recorded by what it declared at `initialize` — self-declared and not
        // authenticated, which is why the screen labels it as such. It reaches the journal only
        // because the transport and the recorder share one holder; with a holder each, every
        // record would claim nobody ever introduced themselves.
        assertEquals("finsight-test-client", journal.records.single().client)
    }

    // ------------------------------------------------------------------ setup

    private val written = mutableListOf<String?>()

    private val journal = RecordingJournal()

    /** One holder, both halves — exactly the wiring `mcpModule` builds. */
    private val declaredClient = DeclaredClientName()

    private fun listening(permission: McpPermission): McpTestClient = runBlocking {
        val port = freePort()
        val controller = McpServerController(
            settings = FakeMcpServerSettingsRepository(enabledSettings(port, token, permission)),
            tools = ToolRegistry(readTools() + writeSurface()),
            resources = noResources(),
            prompts = noPrompts(),
            declaredClient = declaredClient,
        )
        controller.start()
        this@WriteSurfaceTest.controller = controller

        McpTestClient(port, token).also { withContext(Dispatchers.IO) { it.initialize() } }
    }

    private fun writeSurface(): List<McpTool> {
        val accounts = FakeAccountRepository(listOf(checking))
        val creditCards = FakeCreditCardRepository()
        val invoices = FakeInvoiceRepository()
        val transactions = FakeTransactionRepository(listOf(existing))
        val entries = FakeEntryRepository()
        val clock = clockAt(today)

        val resolver = TransactionItemResolver(
            accounts = accounts,
            creditCards = creditCards,
            categories = FakeCategoryRepository(listOf(groceries)),
            invoices = invoices,
            createTransaction = RecordingCreateTransaction { form ->
                written += form.title
                Either.Right(
                    transaction(
                        id = written.size.toLong(),
                        date = LocalDate(2026, 7, 2),
                        entries = listOf(entry(checking, -1_000), entry(expensesAccount, 1_000)),
                        title = form.title,
                    ),
                )
            },
            addInstallment = RecordingAddInstallment { _, _ -> Either.Right(emptyList()) },
            transferBetweenAccounts = TransferBetweenAccountsUseCase(
                transactionRepository = transactions,
                accountRepository = accounts,
                harvestExchangeRate = HarvestExchangeRateUseCase(FakeExchangeRates()),
            ),
            payInvoicePayment = RecordingPayInvoice { error("No invoice is reachable in this fixture") },
            adjustBalance = AdjustBalanceUseCase(transactions, CalculateBalanceUseCase(entries)),
            adjustInvoice = AdjustInvoiceUseCase(transactions, CalculateInvoiceUseCase(entries)),
        )
        val duplicates = ProbableDuplicates(transactions, creditCards)
        val idempotency = IdempotencyStore(clock)
        val activity = ActivityRecorder(journal, clock, declaredClient = { declaredClient.name })

        return listOf(
            RecordTransactionsTool(resolver, duplicates, idempotency, activity),
            PreviewTransactionsTool(resolver, duplicates),
            UpdateTransactionsTool(
                transactions = transactions,
                categories = FakeCategoryRepository(listOf(groceries)),
                creditCards = creditCards,
                invoices = invoices,
                updateTransaction = RecordingUpdateTransaction(),
                idempotency = idempotency,
                activity = activity,
            ),
            DeleteTransactionsTool(
                transactions = transactions,
                deleteTransaction = RecordingDeleteTransaction(),
                idempotency = idempotency,
                activity = activity,
            ),
        )
    }

    private fun readTools(): List<McpTool> {
        val accounts = FakeAccountRepository(
            userAccounts = listOf(checking),
            chart = listOf(checking, expensesAccount, incomesAccount, conversionAccount),
        )
        val categories = FakeCategoryRepository(listOf(groceries))
        val creditCards = FakeCreditCardRepository()
        val invoices = FakeInvoiceRepository()
        val transactions = FakeTransactionRepository(listOf(existing))
        val entries = FakeEntryRepository(accountBalances = mapOf(1L to 1_500.0))
        val clock = clockAt(today)

        return listOf(
            GetOverviewTool(
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
            ),
            ListAccountsTool(accounts, CalculateBalanceUseCase(entries), clock, TEST_ZONE),
            ListCategoriesTool(categories, clock, TEST_ZONE),
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
        )
    }

    private fun McpTestClient.call(id: Int, method: String, params: String? = null) =
        post(request(id, method, params)).body().asJson()["result"]!!.jsonObject

    private fun kotlinx.serialization.json.JsonObject.structuredResult() =
        this["structuredContent"]!!.jsonObject["result"]!!.jsonObject
}
