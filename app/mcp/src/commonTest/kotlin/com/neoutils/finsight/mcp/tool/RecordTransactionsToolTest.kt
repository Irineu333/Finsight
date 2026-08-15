@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.mcp.tool

import arrow.core.Either
import com.neoutils.finsight.domain.error.InvoiceError
import com.neoutils.finsight.domain.error.InvoiceException
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.usecase.AdjustBalanceUseCase
import com.neoutils.finsight.domain.usecase.AdjustInvoiceUseCase
import com.neoutils.finsight.domain.usecase.CalculateBalanceUseCase
import com.neoutils.finsight.domain.usecase.CalculateInvoiceUseCase
import com.neoutils.finsight.domain.usecase.HarvestExchangeRateUseCase
import com.neoutils.finsight.domain.usecase.TransferBetweenAccountsUseCase
import com.neoutils.finsight.mcp.FakeAccountRepository
import com.neoutils.finsight.mcp.FakeCategoryRepository
import com.neoutils.finsight.mcp.FakeCreditCardRepository
import com.neoutils.finsight.mcp.FakeEntryRepository
import com.neoutils.finsight.mcp.FakeExchangeRates
import com.neoutils.finsight.mcp.FakeInvoiceRepository
import com.neoutils.finsight.mcp.FakeTransactionRepository
import com.neoutils.finsight.mcp.FixedClock
import com.neoutils.finsight.mcp.RecordingAddInstallment
import com.neoutils.finsight.mcp.RecordingCreateTransaction
import com.neoutils.finsight.mcp.RecordingJournal
import com.neoutils.finsight.mcp.RecordingPayInvoice
import com.neoutils.finsight.mcp.account
import com.neoutils.finsight.mcp.category
import com.neoutils.finsight.mcp.contract.ToolErrorCategory
import com.neoutils.finsight.mcp.contract.ToolOutcome
import com.neoutils.finsight.mcp.contract.ToolWarningCode
import com.neoutils.finsight.mcp.creditCard
import com.neoutils.finsight.mcp.entry
import com.neoutils.finsight.mcp.expensesAccount
import com.neoutils.finsight.mcp.invoice
import com.neoutils.finsight.mcp.transaction
import com.neoutils.finsight.mcp.write.ActivityRecorder
import com.neoutils.finsight.mcp.write.IdempotencyCodes
import com.neoutils.finsight.mcp.write.IdempotencyStore
import com.neoutils.finsight.mcp.write.TransactionItemResolver
import com.neoutils.finsight.mcp.write.WriteItemCodes
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * The batch write, item by item.
 *
 * What is asserted throughout is the **per-item** outcome: a call that answered "3 items
 * recorded" and nothing else would satisfy every one of these assertions about counts and
 * none of the ones that matter.
 */
class RecordTransactionsToolTest {

    private val checking = account(1, "Checking", currency = "BRL", isDefault = true)
    private val cardAccount = account(3, "Card", currency = "BRL", type = AccountType.LIABILITY)
    private val card = creditCard(10, "Blue", accountId = cardAccount.id)
    private val groceries = category(20, "Groceries", type = Category.Type.EXPENSE)
    private val julyBill = invoice(30, card, dueMonth = YearMonth(2026, 7), status = Invoice.Status.OPEN)

    private val existing = transaction(
        id = 99,
        date = LocalDate(2026, 6, 28),
        entries = listOf(entry(checking, -1_000), entry(expensesAccount, 1_000, groceries.dimensionId)),
        title = "Market",
    )

    private var nextId = 0L

    /** Refuses the item whose title is `refused`; records everything else. */
    private val createTransaction = RecordingCreateTransaction { form ->
        if (form.title == "refused") {
            Either.Left(InvoiceException(InvoiceError.BlockedInvoice(Invoice.Status.CLOSED)))
        } else {
            nextId += 1
            Either.Right(
                transaction(
                    id = nextId,
                    date = LocalDate(2026, 6, 28),
                    entries = listOf(entry(checking, -1_000), entry(expensesAccount, 1_000)),
                    title = form.title,
                ),
            )
        }
    }

    private val journal = RecordingJournal()

    // ------------------------------------------------------------------ batch

    @Test
    fun `one call records many items, and answers the outcome of each one`() = runTest {
        val outcome = tool().execute(
            arguments("""
                {"items":[
                  {"intent":"EXPENSE","date":"2026-06-28","accountId":1,"amount":{"currency":"BRL","minorUnits":1000},"description":"A"},
                  {"intent":"EXPENSE","date":"2026-06-28","accountId":1,"amount":{"currency":"BRL","minorUnits":2000},"description":"B"},
                  {"intent":"INCOME","date":"2026-06-28","accountId":1,"amount":{"currency":"BRL","minorUnits":3000},"description":"C"}
                ]}
            """),
        )

        val result = assertIs<ToolOutcome.Ok>(outcome).result
        assertEquals(3, result["appliedCount"]!!.jsonPrimitive.content.toInt())
        assertEquals(0, result["refusedCount"]!!.jsonPrimitive.content.toInt())

        val items = result["items"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf(0, 1, 2), items.map { it["index"]!!.jsonPrimitive.content.toInt() })
        assertEquals(List(3) { "APPLIED" }, items.map { it["status"]!!.jsonPrimitive.content })
        // Every item names what it wrote: a batch answer with no identifiers would not let
        // the caller reach what it created.
        assertEquals(listOf("1", "2", "3"), items.map { it["transactionIds"]!!.jsonArray.single().jsonPrimitive.content })
    }

    @Test
    fun `each item goes through the same use case the single operation would`() = runTest {
        tool().execute(
            arguments("""
                {"items":[
                  {"intent":"EXPENSE","date":"2026-06-28","accountId":1,"categoryId":20,"amount":{"currency":"BRL","minorUnits":1000}},
                  {"intent":"EXPENSE","date":"2026-06-28","accountId":1,"categoryId":20,"amount":{"currency":"BRL","minorUnits":2000}}
                ]}
            """),
        )

        assertEquals(2, createTransaction.forms.size)
        assertEquals(listOf("1000", "2000"), createTransaction.forms.map { it.amount })
        assertEquals(listOf(groceries, groceries), createTransaction.forms.map { it.category })
    }

    @Test
    fun `an item refused by the domain does not bring down the batch`() = runTest {
        val outcome = tool().execute(
            arguments("""
                {"items":[
                  {"intent":"EXPENSE","date":"2026-06-28","accountId":1,"amount":{"currency":"BRL","minorUnits":1000},"description":"A"},
                  {"intent":"EXPENSE","date":"2026-06-28","accountId":1,"amount":{"currency":"BRL","minorUnits":2000},"description":"refused"},
                  {"intent":"EXPENSE","date":"2026-06-28","accountId":1,"amount":{"currency":"BRL","minorUnits":3000},"description":"C"}
                ]}
            """),
        )

        val result = assertIs<ToolOutcome.Ok>(outcome).result
        val items = result["items"]!!.jsonArray.map { it.jsonObject }

        assertEquals(2, result["appliedCount"]!!.jsonPrimitive.content.toInt())
        assertEquals(listOf("APPLIED", "REFUSED", "APPLIED"), items.map { it["status"]!!.jsonPrimitive.content })

        val refusal = items[1]["error"]!!.jsonObject
        assertEquals(ToolErrorCategory.DOMAIN_RULE.name, refusal["category"]!!.jsonPrimitive.content)
        assertEquals(false, refusal["isRetryable"]!!.jsonPrimitive.content.toBoolean())
    }

    @Test
    fun `an item naming a category that does not exist is refused, and none is created`() = runTest {
        val outcome = tool().execute(
            arguments("""
                {"items":[{"intent":"EXPENSE","date":"2026-06-28","accountId":1,"categoryId":404,
                           "amount":{"currency":"BRL","minorUnits":1000}}]}
            """),
        )

        val item = assertIs<ToolOutcome.Ok>(outcome).result["items"]!!.jsonArray.single().jsonObject
        assertEquals("REFUSED", item["status"]!!.jsonPrimitive.content)
        assertEquals(WriteItemCodes.CATEGORY_NOT_FOUND, item["error"]!!.jsonObject["code"]!!.jsonPrimitive.content)
        assertTrue(createTransaction.forms.isEmpty(), "Nothing was written for a refused item")
    }

    // ------------------------------------------------------------- duplicates

    @Test
    fun `a probable duplicate is a warning on the item, and the item is written`() = runTest {
        val outcome = tool(transactions = FakeTransactionRepository(listOf(existing))).execute(
            arguments("""
                {"items":[{"intent":"EXPENSE","date":"2026-06-28","accountId":1,"description":"Market",
                           "amount":{"currency":"BRL","minorUnits":1000}}]}
            """),
        )

        val ok = assertIs<ToolOutcome.Ok>(outcome)
        val item = ok.result["items"]!!.jsonArray.single().jsonObject

        assertEquals("APPLIED", item["status"]!!.jsonPrimitive.content)
        val warning = item["warnings"]!!.jsonArray.single().jsonObject
        assertEquals(ToolWarningCode.PROBABLE_DUPLICATE.name, warning["code"]!!.jsonPrimitive.content)
        assertEquals("99", warning["details"]!!.jsonObject["transactionId"]!!.jsonPrimitive.content)
        assertEquals(1, createTransaction.forms.size)
    }

    @Test
    fun `an item that differs in description is not called a duplicate`() = runTest {
        val outcome = tool(transactions = FakeTransactionRepository(listOf(existing))).execute(
            arguments("""
                {"items":[{"intent":"EXPENSE","date":"2026-06-28","accountId":1,"description":"Pharmacy",
                           "amount":{"currency":"BRL","minorUnits":1000}}]}
            """),
        )

        val item = assertIs<ToolOutcome.Ok>(outcome).result["items"]!!.jsonArray.single().jsonObject
        assertNull(item["warnings"])
    }

    // ------------------------------------------------------------ idempotency

    @Test
    fun `the same call with the same key writes nothing again and answers the first response`() = runTest {
        val tool = tool()
        val call = arguments("""
            {"idempotencyKey":"batch-1","items":[
              {"intent":"EXPENSE","date":"2026-06-28","accountId":1,"amount":{"currency":"BRL","minorUnits":1000}}
            ]}
        """)

        val first = assertIs<ToolOutcome.Ok>(tool.execute(call))
        val second = assertIs<ToolOutcome.Ok>(tool.execute(call))

        assertEquals(first.result, second.result)
        assertEquals(1, createTransaction.forms.size, "The repeat re-executed the write")
    }

    @Test
    fun `the same key with different items is a conflict, and nothing is written`() = runTest {
        val tool = tool()
        tool.execute(
            arguments("""
                {"idempotencyKey":"batch-1","items":[
                  {"intent":"EXPENSE","date":"2026-06-28","accountId":1,"amount":{"currency":"BRL","minorUnits":1000}}
                ]}
            """),
        )

        val outcome = tool.execute(
            arguments("""
                {"idempotencyKey":"batch-1","items":[
                  {"intent":"EXPENSE","date":"2026-06-29","accountId":1,"amount":{"currency":"BRL","minorUnits":7777}}
                ]}
            """),
        )

        val failed = assertIs<ToolOutcome.Failed>(outcome)
        assertEquals(ToolErrorCategory.CONFLICT, failed.error.category)
        assertEquals(IdempotencyCodes.KEY_REUSED_WITH_DIFFERENT_ARGUMENTS, failed.error.code)
        assertEquals(1, createTransaction.forms.size, "The second consignment was written despite the conflict")
    }

    @Test
    fun `an interrupted batch is resumed by the same key, and writes only what was missing`() = runTest {
        // The third item breaks the call outright — a process that died halfway leaves
        // exactly this state: two items written, and no record of the call as a whole.
        var breakThirdItem = true
        val written = mutableListOf<String>()
        val creating = RecordingCreateTransaction { form ->
            if (breakThirdItem && form.title == "C") throw IllegalStateException("the process died")
            form.title?.let { written += it }
            nextId += 1
            Either.Right(
                transaction(
                    id = nextId,
                    date = LocalDate(2026, 6, 28),
                    entries = listOf(entry(checking, -1_000), entry(expensesAccount, 1_000)),
                    title = form.title,
                ),
            )
        }
        val tool = tool(createTransaction = creating)
        val call = arguments("""
            {"idempotencyKey":"statement-june","items":[
              {"intent":"EXPENSE","date":"2026-06-28","accountId":1,"amount":{"currency":"BRL","minorUnits":1000},"description":"A"},
              {"intent":"EXPENSE","date":"2026-06-28","accountId":1,"amount":{"currency":"BRL","minorUnits":2000},"description":"B"},
              {"intent":"EXPENSE","date":"2026-06-28","accountId":1,"amount":{"currency":"BRL","minorUnits":3000},"description":"C"}
            ]}
        """)

        runCatching { tool.execute(call) }
        assertEquals(listOf("A", "B"), written)

        breakThirdItem = false
        val resumed = assertIs<ToolOutcome.Ok>(tool.execute(call))
        val items = resumed.result["items"]!!.jsonArray.map { it.jsonObject }

        assertEquals(
            listOf("SKIPPED_AS_DUPLICATE", "SKIPPED_AS_DUPLICATE", "APPLIED"),
            items.map { it["status"]!!.jsonPrimitive.content },
        )
        assertEquals(1, resumed.result["appliedCount"]!!.jsonPrimitive.content.toInt())
        assertEquals(2, resumed.result["skippedAsDuplicateCount"]!!.jsonPrimitive.content.toInt())
        // What was applied stayed applied, and nothing was written twice.
        assertEquals(listOf("A", "B", "C"), written)
        assertEquals(listOf("1", "2"), items.take(2).map { it["transactionIds"]!!.jsonArray.single().jsonPrimitive.content })
    }

    // ------------------------------------------------------------------ shape

    @Test
    fun `a call with no items is refused before anything is written`() = runTest {
        val outcome = tool().execute(arguments("""{"items":[]}"""))

        val failed = assertIs<ToolOutcome.Failed>(outcome)
        assertEquals(BatchCodes.ITEMS_REQUIRED, failed.error.code)
        assertTrue(createTransaction.forms.isEmpty())
    }

    @Test
    fun `a batch above the ceiling is refused, naming it, and nothing is written`() = runTest {
        val items = (0..MAX_ITEMS_PER_CALL).joinToString(",") {
            """{"intent":"EXPENSE","date":"2026-06-28","accountId":1,"amount":{"currency":"BRL","minorUnits":100}}"""
        }
        val outcome = tool().execute(arguments("""{"items":[$items]}"""))

        val failed = assertIs<ToolOutcome.Failed>(outcome)
        assertEquals(BatchCodes.TOO_MANY_ITEMS, failed.error.code)
        assertTrue(failed.error.message.contains("$MAX_ITEMS_PER_CALL"))
        assertTrue(createTransaction.forms.isEmpty())
    }

    @Test
    fun `it announces that it writes and that it is idempotent`() {
        val annotations = tool().annotations

        assertEquals(false, annotations.readOnlyHint)
        assertEquals(true, annotations.idempotentHint)
        assertEquals(false, annotations.destructiveHint)
    }

    // ---------------------------------------------------------------- journal

    @Test
    fun `one call leaves one record, naming every transaction it wrote`() = runTest {
        tool().execute(
            arguments("""
                {"items":[
                  {"intent":"EXPENSE","date":"2026-06-28","accountId":1,"amount":{"currency":"BRL","minorUnits":1000}},
                  {"intent":"EXPENSE","date":"2026-06-28","accountId":1,"amount":{"currency":"BRL","minorUnits":2000}}
                ]}
            """),
        )

        val record = journal.records.single()
        assertEquals(RECORD_TRANSACTIONS_TOOL, record.tool)
        assertEquals(listOf("transaction:1", "transaction:2"), record.affected)
    }

    @Test
    fun `a call refused as a conflict is recorded too`() = runTest {
        val tool = tool()
        val first = arguments("""
            {"idempotencyKey":"k","items":[
              {"intent":"EXPENSE","date":"2026-06-28","accountId":1,"amount":{"currency":"BRL","minorUnits":1000}}
            ]}
        """)
        tool.execute(first)
        tool.execute(
            arguments("""
                {"idempotencyKey":"k","items":[
                  {"intent":"INCOME","date":"2026-06-28","accountId":1,"amount":{"currency":"BRL","minorUnits":1000}}
                ]}
            """),
        )

        assertEquals(2, journal.records.size)
        assertEquals("REFUSED", journal.records.last().outcome.name)
    }

    // ------------------------------------------------------------------ setup

    private fun tool(
        transactions: FakeTransactionRepository = FakeTransactionRepository(),
        createTransaction: RecordingCreateTransaction = this.createTransaction,
    ): RecordTransactionsTool {
        val accounts = FakeAccountRepository(listOf(checking, cardAccount))
        val creditCards = FakeCreditCardRepository(listOf(card))
        val invoices = FakeInvoiceRepository(listOf(julyBill))
        val entries = FakeEntryRepository()
        val clock = FixedClock(Instant.parse("2026-06-28T12:00:00Z"))

        return RecordTransactionsTool(
            resolver = TransactionItemResolver(
                accounts = accounts,
                creditCards = creditCards,
                categories = FakeCategoryRepository(listOf(groceries)),
                invoices = invoices,
                createTransaction = createTransaction,
                addInstallment = RecordingAddInstallment { _, _ -> Either.Right(emptyList()) },
                transferBetweenAccounts = TransferBetweenAccountsUseCase(
                    transactionRepository = transactions,
                    accountRepository = accounts,
                    harvestExchangeRate = HarvestExchangeRateUseCase(FakeExchangeRates()),
                ),
                payInvoicePayment = RecordingPayInvoice { Either.Right(julyBill) },
                adjustBalance = AdjustBalanceUseCase(transactions, CalculateBalanceUseCase(entries)),
                adjustInvoice = AdjustInvoiceUseCase(transactions, CalculateInvoiceUseCase(entries)),
            ),
            duplicates = ProbableDuplicates(transactions, creditCards),
            idempotency = IdempotencyStore(clock),
            activity = ActivityRecorder(journal, clock),
        )
    }

    private fun arguments(raw: String): JsonObject = Json.parseToJsonElement(raw.trimIndent()) as JsonObject
}
