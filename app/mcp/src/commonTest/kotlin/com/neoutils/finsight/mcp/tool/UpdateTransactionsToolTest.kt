@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.mcp.tool

import arrow.core.Either
import com.neoutils.finsight.domain.error.ClosedAccountException
import com.neoutils.finsight.domain.error.ClosedFacade
import com.neoutils.finsight.domain.error.LedgerError
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.TransactionTarget
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.mcp.FakeCategoryRepository
import com.neoutils.finsight.mcp.FakeCreditCardRepository
import com.neoutils.finsight.mcp.FakeInvoiceRepository
import com.neoutils.finsight.mcp.FakeTransactionRepository
import com.neoutils.finsight.mcp.FixedClock
import com.neoutils.finsight.mcp.RecordingJournal
import com.neoutils.finsight.mcp.RecordingUpdateTransaction
import com.neoutils.finsight.mcp.account
import com.neoutils.finsight.mcp.category
import com.neoutils.finsight.mcp.contract.ToolErrorCategory
import com.neoutils.finsight.mcp.contract.ToolOutcome
import com.neoutils.finsight.mcp.creditCard
import com.neoutils.finsight.mcp.entry
import com.neoutils.finsight.mcp.expensesAccount
import com.neoutils.finsight.mcp.invoice
import com.neoutils.finsight.mcp.transaction
import com.neoutils.finsight.mcp.write.ActivityRecorder
import com.neoutils.finsight.mcp.write.IdempotencyStore
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
 * The alteration, and above all **what it refuses to alter**.
 *
 * Every field this tool does not offer is asserted to arrive at the use case exactly as the
 * ledger held it: the point of not offering the amount is that the amount does not move.
 */
class UpdateTransactionsToolTest {

    private val checking = account(1, "Checking", currency = "BRL", isDefault = true)
    private val savings = account(2, "Savings", currency = "BRL")
    private val cardAccount = account(3, "Card", currency = "BRL", type = AccountType.LIABILITY)
    private val equity = account(904, "Reconciliação", type = AccountType.EQUITY)
    private val card = creditCard(10, "Blue", accountId = cardAccount.id)
    private val groceries = category(20, "Groceries", type = Category.Type.EXPENSE)
    private val salary = category(21, "Salary", type = Category.Type.INCOME)
    private val julyBill = invoice(30, card, dueMonth = YearMonth(2026, 7), status = Invoice.Status.OPEN)

    private val expense = transaction(
        id = 1,
        date = LocalDate(2026, 6, 28),
        entries = listOf(entry(checking, -1_500), entry(expensesAccount, 1_500, groceries.dimensionId)),
        title = "Market",
    )

    private val transfer = transaction(
        id = 2,
        date = LocalDate(2026, 6, 28),
        entries = listOf(entry(checking, -1_000), entry(savings, 1_000)),
    )

    private val adjustment = transaction(
        id = 3,
        date = LocalDate(2026, 6, 28),
        entries = listOf(entry(checking, 1_000), entry(equity, -1_000)),
    )

    private val installmentPayment = transaction(
        id = 4,
        date = LocalDate(2026, 6, 28),
        entries = listOf(entry(cardAccount, -1_000, julyBill.dimensionId), entry(expensesAccount, 1_000)),
        installmentId = 7,
        installmentNumber = 1,
    )

    private val cardPurchase = transaction(
        id = 5,
        date = LocalDate(2026, 6, 28),
        entries = listOf(entry(cardAccount, -2_000, julyBill.dimensionId), entry(expensesAccount, 2_000)),
        title = "Fuel",
    )

    private val updateTransaction = RecordingUpdateTransaction()
    private val journal = RecordingJournal()

    // ------------------------------------------------------- the three fields

    @Test
    fun `it alters the category, the description and the date, and carries everything else over`() = runTest {
        val outcome = tool().execute(
            arguments("""{"items":[{"transactionId":1,"categoryId":20,"description":"Groceries run","date":"2026-07-01"}]}"""),
        )

        val item = assertIs<ToolOutcome.Ok>(outcome).result["items"]!!.jsonArray.single().jsonObject
        assertEquals("APPLIED", item["status"]!!.jsonPrimitive.content)

        val (id, form) = updateTransaction.calls.single()
        assertEquals(1L, id)
        assertEquals("Groceries run", form.title)
        assertEquals("01/07/2026", form.date)
        assertEquals(groceries, form.category)
        // Untouched, and read straight off the ledger: money is not this tool's to change.
        assertEquals("1500", form.amount)
        assertEquals(checking, form.account)
        assertEquals(TransactionType.EXPENSE, form.type)
        assertEquals(TransactionTarget.ACCOUNT, form.target)
    }

    @Test
    fun `a field left out is left as it was`() = runTest {
        tool().execute(arguments("""{"items":[{"transactionId":1,"date":"2026-07-01"}]}"""))

        val form = updateTransaction.calls.single().second
        assertEquals("Market", form.title)
        assertEquals(groceries, form.category)
    }

    @Test
    fun `an explicit null category leaves the transaction unclassified`() = runTest {
        tool().execute(arguments("""{"items":[{"transactionId":1,"categoryId":null}]}"""))

        assertNull(updateTransaction.calls.single().second.category)
    }

    @Test
    fun `a card purchase keeps its card and the bill it landed in`() = runTest {
        tool().execute(arguments("""{"items":[{"transactionId":5,"description":"Petrol"}]}"""))

        val form = updateTransaction.calls.single().second
        assertEquals(TransactionTarget.CREDIT_CARD, form.target)
        assertEquals(card, form.creditCard)
        assertEquals(julyBill.dueMonth, form.invoiceDueMonth)
        assertEquals("2000", form.amount)
    }

    // ------------------------------------------------------- what it will not

    @Test
    fun `an item naming an amount or an account is refused, not ignored`() = runTest {
        val outcome = tool().execute(
            arguments("""
                {"items":[
                  {"transactionId":1,"amount":{"currency":"BRL","minorUnits":9999}},
                  {"transactionId":1,"accountId":2}
                ]}
            """),
        )

        val items = assertIs<ToolOutcome.Ok>(outcome).result["items"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf("REFUSED", "REFUSED"), items.map { it["status"]!!.jsonPrimitive.content })
        items.forEach {
            assertEquals(
                UpdateTransactionCodes.FIELD_NOT_ALTERABLE,
                it["error"]!!.jsonObject["code"]!!.jsonPrimitive.content,
            )
        }
        assertTrue(updateTransaction.calls.isEmpty(), "Nothing was written for a refused item")
    }

    @Test
    fun `the input schema offers neither amount nor account as an alterable field`() {
        val item = tool().inputSchema["properties"]!!.jsonObject[ITEMS]!!.jsonObject["items"]!!.jsonObject
        val fields = item["properties"]!!.jsonObject.keys

        assertEquals(setOf("transactionId", "categoryId", "description", "date"), fields)
    }

    @Test
    fun `a transaction a rewrite cannot express is refused, and the batch goes on`() = runTest {
        val outcome = tool().execute(
            arguments("""
                {"items":[
                  {"transactionId":2,"description":"x"},
                  {"transactionId":3,"description":"x"},
                  {"transactionId":4,"description":"x"},
                  {"transactionId":1,"description":"ok"}
                ]}
            """),
        )

        val result = assertIs<ToolOutcome.Ok>(outcome).result
        val items = result["items"]!!.jsonArray.map { it.jsonObject }

        assertEquals(listOf("REFUSED", "REFUSED", "REFUSED", "APPLIED"), items.map { it["status"]!!.jsonPrimitive.content })
        items.take(3).forEach {
            assertEquals(
                UpdateTransactionCodes.NOT_REWRITABLE,
                it["error"]!!.jsonObject["code"]!!.jsonPrimitive.content,
            )
        }
        assertEquals(1, updateTransaction.calls.size)
    }

    @Test
    fun `an item that changes nothing is refused`() = runTest {
        val outcome = tool().execute(arguments("""{"items":[{"transactionId":1}]}"""))

        val item = assertIs<ToolOutcome.Ok>(outcome).result["items"]!!.jsonArray.single().jsonObject
        assertEquals(UpdateTransactionCodes.NOTHING_TO_UPDATE, item["error"]!!.jsonObject["code"]!!.jsonPrimitive.content)
    }

    @Test
    fun `a category that does not classify this direction is refused rather than dropped`() = runTest {
        val outcome = tool().execute(arguments("""{"items":[{"transactionId":1,"categoryId":21}]}"""))

        val item = assertIs<ToolOutcome.Ok>(outcome).result["items"]!!.jsonArray.single().jsonObject
        assertEquals(
            WriteItemCodes.CATEGORY_TYPE_MISMATCH,
            item["error"]!!.jsonObject["code"]!!.jsonPrimitive.content,
        )
        assertTrue(updateTransaction.calls.isEmpty())
    }

    @Test
    fun `an unknown transaction is refused as not found`() = runTest {
        val outcome = tool().execute(arguments("""{"items":[{"transactionId":404,"description":"x"}]}"""))

        val error = assertIs<ToolOutcome.Ok>(outcome).result["items"]!!.jsonArray.single()
            .jsonObject["error"]!!.jsonObject
        assertEquals(BatchCodes.TRANSACTION_NOT_FOUND, error["code"]!!.jsonPrimitive.content)
        assertEquals(ToolErrorCategory.NOT_FOUND.name, error["category"]!!.jsonPrimitive.content)
    }

    @Test
    fun `a refusal of the domain comes back as the domain wrote it`() = runTest {
        val refusing = RecordingUpdateTransaction { _, _ ->
            Either.Left(ClosedAccountException(LedgerError.ClosedAccount(ClosedFacade.ACCOUNT)))
        }
        val outcome = tool(updateTransaction = refusing).execute(
            arguments("""{"items":[{"transactionId":1,"description":"x"}]}"""),
        )

        val error = assertIs<ToolOutcome.Ok>(outcome).result["items"]!!.jsonArray.single()
            .jsonObject["error"]!!.jsonObject
        assertEquals(ToolErrorCategory.DOMAIN_RULE.name, error["category"]!!.jsonPrimitive.content)
        assertEquals(false, error["isRetryable"]!!.jsonPrimitive.content.toBoolean())
    }

    @Test
    fun `it announces that it writes, overwrites and is idempotent`() {
        val annotations = tool().annotations

        assertEquals(false, annotations.readOnlyHint)
        assertEquals(true, annotations.destructiveHint)
        assertEquals(true, annotations.idempotentHint)
    }

    @Test
    fun `one call leaves one record naming what it touched`() = runTest {
        tool().execute(arguments("""{"items":[{"transactionId":1,"description":"x"}]}"""))

        val record = journal.records.single()
        assertEquals(UPDATE_TRANSACTIONS_TOOL, record.tool)
        assertEquals(listOf("transaction:1"), record.affected)
    }

    // ------------------------------------------------------------------ setup

    private fun tool(
        updateTransaction: RecordingUpdateTransaction = this.updateTransaction,
    ): UpdateTransactionsTool {
        val clock = FixedClock(Instant.parse("2026-06-28T12:00:00Z"))

        return UpdateTransactionsTool(
            transactions = FakeTransactionRepository(
                listOf(expense, transfer, adjustment, installmentPayment, cardPurchase),
            ),
            categories = FakeCategoryRepository(listOf(groceries, salary)),
            creditCards = FakeCreditCardRepository(listOf(card)),
            invoices = FakeInvoiceRepository(listOf(julyBill)),
            updateTransaction = updateTransaction,
            idempotency = IdempotencyStore(clock),
            activity = ActivityRecorder(journal, clock),
        )
    }

    private fun arguments(raw: String): JsonObject = Json.parseToJsonElement(raw.trimIndent()) as JsonObject
}
