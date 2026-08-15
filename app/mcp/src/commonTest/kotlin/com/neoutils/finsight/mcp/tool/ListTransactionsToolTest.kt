@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.mcp.tool

import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.mcp.FakeAccountRepository
import com.neoutils.finsight.mcp.FakeCategoryRepository
import com.neoutils.finsight.mcp.FakeCreditCardRepository
import com.neoutils.finsight.mcp.FakeInvoiceRepository
import com.neoutils.finsight.mcp.FakeTransactionRepository
import com.neoutils.finsight.mcp.TEST_ZONE
import com.neoutils.finsight.mcp.account
import com.neoutils.finsight.mcp.category
import com.neoutils.finsight.mcp.clockAt
import com.neoutils.finsight.mcp.contract.ToolOutcome
import com.neoutils.finsight.mcp.creditCard
import com.neoutils.finsight.mcp.entry
import com.neoutils.finsight.mcp.expensesAccount
import com.neoutils.finsight.mcp.incomesAccount
import com.neoutils.finsight.mcp.invoice
import com.neoutils.finsight.mcp.transaction
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
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

class ListTransactionsToolTest {

    private val today = LocalDate(2026, 7, 15)

    private val checking = account(1, "Checking")
    // A card *is* its `LIABILITY` account in the chart — that is what the invoice leg posts to.
    private val cardAccount = account(3, "Card", type = AccountType.LIABILITY)
    private val card = creditCard(10, "Blue", accountId = cardAccount.id)
    private val groceries = category(20, "Groceries")
    private val salary = category(21, "Salary", type = com.neoutils.finsight.domain.model.Category.Type.INCOME)
    private val julyBill = invoice(30, card, dueMonth = YearMonth(2026, 7), status = Invoice.Status.OPEN)

    /** Money leaving an account: the asset leg is credited, the expense leg debited. */
    private val expense = transaction(
        id = 1,
        date = LocalDate(2026, 6, 20),
        title = "Market",
        entries = listOf(entry(checking, -5_000), entry(expensesAccount, 5_000, groceries.dimensionId)),
    )

    /** Money arriving: the asset leg is debited and the income leg credited. */
    private val income = transaction(
        id = 2,
        date = LocalDate(2026, 6, 25),
        title = "Salary",
        entries = listOf(entry(checking, 300_000), entry(incomesAccount, -300_000, salary.dimensionId)),
    )

    /** A purchase on the card: the liability leg carries the bill, the nominal one the category. */
    private val purchase = transaction(
        id = 3,
        date = LocalDate(2026, 6, 28),
        title = "Fuel",
        entries = listOf(
            entry(cardAccount, -12_000, julyBill.dimensionId),
            entry(expensesAccount, 12_000, groceries.dimensionId),
        ),
    )

    /** An expense nobody classified: the nominal leg carries no dimension at all. */
    private val unclassified = transaction(
        id = 4,
        date = LocalDate(2026, 6, 29),
        entries = listOf(entry(checking, -900), entry(expensesAccount, 900)),
        title = "Kiosk",
    )

    @Test
    fun `spending reads negative and income positive`() = runTest {
        val items = tool().items()

        assertEquals(-5_000, items.byId(1)["amount"]!!.jsonObject.minorUnits())
        assertEquals(300_000, items.byId(2)["amount"]!!.jsonObject.minorUnits())
        assertEquals(-12_000, items.byId(3)["amount"]!!.jsonObject.minorUnits())
    }

    @Test
    fun `a card purchase carries both of its dates, and the filter says which it cut on`() = runTest {
        val result = tool().run()
        val item = result["transactions"]!!.jsonArray.map { it.jsonObject }.byId(3)

        assertEquals("2026-06-28", item["date"]!!.jsonPrimitive.content)
        assertEquals(julyBill.dueDate.toString(), item["invoice"]!!.jsonObject["dueDate"]!!.jsonPrimitive.content)
        assertEquals("2026-07", item["invoice"]!!.jsonObject["dueMonth"]!!.jsonPrimitive.content)
        assertEquals(TransactionDateField.TRANSACTION_DATE.name, result["filteredOn"]!!.jsonPrimitive.content)
    }

    @Test
    fun `every nested facade carries its identifier beside its name`() = runTest {
        val item = tool().items().byId(3)

        assertEquals(10, item["creditCard"]!!.jsonObject["id"]!!.jsonPrimitive.content.toInt())
        assertEquals("Blue", item["creditCard"]!!.jsonObject["name"]!!.jsonPrimitive.content)
        assertEquals(20, item["category"]!!.jsonObject["id"]!!.jsonPrimitive.content.toInt())
        assertEquals("Groceries", item["category"]!!.jsonObject["name"]!!.jsonPrimitive.content)
    }

    @Test
    fun `the label comes back derived, and no leg is ever exposed`() = runTest {
        val items = tool().items()

        assertEquals("EXPENSE", items.byId(1)["label"]!!.jsonPrimitive.content)
        assertEquals("INCOME", items.byId(2)["label"]!!.jsonPrimitive.content)
        items.forEach { item ->
            assertNull(item["entries"], "A ledger leg never crosses this boundary")
            assertNull(item["legs"])
        }
    }

    @Test
    fun `the category filter tells the three states apart`() = runTest {
        assertEquals(setOf(1, 2, 3, 4), tool().items().ids())
        assertEquals(setOf(1, 3), tool().items("""{"category":"CATEGORY","categoryId":20}""").ids())
        assertEquals(setOf(4), tool().items("""{"category":"UNCATEGORIZED"}""").ids())
    }

    @Test
    fun `being unclassified is the absence of a classification and never a named category`() = runTest {
        val item = tool().items().byId(4)

        assertNull(item["category"])
        assertEquals("true", item["isUncategorized"]!!.jsonPrimitive.content)
    }

    @Test
    fun `a card is its liability account, not a filter of its own`() = runTest {
        assertEquals(setOf(3), tool().items("""{"creditCardId":10}""").ids())
        assertEquals(setOf(1, 2, 4), tool().items("""{"accountId":1}""").ids())
    }

    @Test
    fun `a bill is read by its identifier`() = runTest {
        assertEquals(setOf(3), tool().items("""{"invoiceId":30}""").ids())
    }

    @Test
    fun `the amount range cuts by the transaction's magnitude`() = runTest {
        assertEquals(setOf(1, 3), tool().items("""{"minAmount":10,"maxAmount":200}""").ids())
    }

    @Test
    fun `a period is echoed as asked for, not as assumed`() = runTest {
        val result = tool().run("""{"startDate":"2026-06-25","endDate":"2026-06-28"}""")

        assertEquals(setOf(2, 3), result["transactions"]!!.jsonArray.map { it.jsonObject }.ids())
        val period = result["assumed"]!!.jsonObject["period"]!!.jsonObject
        assertEquals("false", period["wasAssumed"]!!.jsonPrimitive.content)
        assertEquals("2026-06-25", period["value"]!!.jsonObject["start"]!!.jsonPrimitive.content)
    }

    @Test
    fun `two filters naming the account are refused rather than one being ignored`() = runTest {
        val outcome = tool().execute(json("""{"accountId":1,"creditCardId":10}"""))

        val failed = assertIs<ToolOutcome.Failed>(outcome)
        assertEquals(ListTransactionsCodes.AMBIGUOUS_ACCOUNT_FILTER, failed.error.code)
    }

    @Test
    fun `an identifier that names nothing is not found`() = runTest {
        val failed = assertIs<ToolOutcome.Failed>(tool().execute(json("""{"invoiceId":999}""")))
        assertEquals(CommonToolCodes.NOT_FOUND, failed.error.code)
    }

    @Test
    fun `the description names the aggregation tool as the way to a total`() {
        assertContains(tool().description, AGGREGATE_TRANSACTIONS_TOOL)
    }

    @Test
    fun `the tool announces itself as read-only`() {
        assertTrue(tool().annotations.readOnlyHint)
        assertTrue(!tool().annotations.destructiveHint)
    }

    private fun tool() = ListTransactionsTool(
        transactions = FakeTransactionRepository(listOf(expense, income, purchase, unclassified)),
        accounts = FakeAccountRepository(listOf(checking), chart = listOf(checking, cardAccount, expensesAccount, incomesAccount)),
        creditCards = FakeCreditCardRepository(listOf(card)),
        invoices = FakeInvoiceRepository(listOf(julyBill)),
        categories = FakeCategoryRepository(listOf(groceries, salary)),
        clock = clockAt(today),
        timeZone = TEST_ZONE,
    )

    private suspend fun ListTransactionsTool.run(arguments: String = "{}"): JsonObject =
        assertIs<ToolOutcome.Ok>(execute(json(arguments))).result

    private suspend fun ListTransactionsTool.items(arguments: String = "{}"): List<JsonObject> =
        run(arguments)["transactions"]!!.jsonArray.map { it.jsonObject }

    private fun List<JsonObject>.byId(id: Int): JsonObject =
        first { it["id"]!!.jsonPrimitive.content.toInt() == id }

    private fun List<JsonObject>.ids(): Set<Int> = map { it["id"]!!.jsonPrimitive.content.toInt() }.toSet()

    private fun JsonObject.minorUnits(): Long = this["minorUnits"]!!.jsonPrimitive.content.toLong()

    private fun json(raw: String): JsonObject = Json.parseToJsonElement(raw) as JsonObject
}
