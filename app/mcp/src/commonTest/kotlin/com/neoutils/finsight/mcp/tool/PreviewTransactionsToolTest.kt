@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.mcp.tool

import arrow.core.Either
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
import com.neoutils.finsight.mcp.contract.ToolOutcome
import com.neoutils.finsight.mcp.contract.ToolWarningCode
import com.neoutils.finsight.mcp.creditCard
import com.neoutils.finsight.mcp.entry
import com.neoutils.finsight.mcp.expensesAccount
import com.neoutils.finsight.mcp.invoice
import com.neoutils.finsight.mcp.transaction
import com.neoutils.finsight.mcp.write.ActivityRecorder
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
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * The dry run: the same answer the write would give, and no write.
 *
 * The strongest assertion here is the negative one — the recording use cases are never
 * entered, whatever the arguments — because that is exactly what the read-only annotation
 * promises a client that decided not to ask the user for confirmation.
 */
class PreviewTransactionsToolTest {

    private val checking = account(1, "Checking", currency = "BRL", isDefault = true)
    private val cardAccount = account(3, "Card", currency = "BRL", type = AccountType.LIABILITY)
    private val card = creditCard(10, "Blue", accountId = cardAccount.id, closingDay = 10, dueDay = 20)
    private val groceries = category(20, "Groceries", type = Category.Type.EXPENSE)
    private val julyBill = invoice(30, card, dueMonth = YearMonth(2026, 7), status = Invoice.Status.OPEN)

    private val existing = transaction(
        id = 99,
        date = LocalDate(2026, 6, 28),
        entries = listOf(entry(checking, -1_000), entry(expensesAccount, 1_000, groceries.dimensionId)),
        title = "Market",
    )

    private val createTransaction = RecordingCreateTransaction { error("The dry run wrote something") }
    private val addInstallment = RecordingAddInstallment { _, _ -> error("The dry run wrote something") }

    @Test
    fun `it describes every item and persists nothing`() = runTest {
        val outcome = preview().execute(
            arguments("""
                {"items":[
                  {"intent":"EXPENSE","date":"2026-06-28","accountId":1,"categoryId":20,"description":"Market","amount":{"currency":"BRL","minorUnits":1000}},
                  {"intent":"CARD_PURCHASE","date":"2026-06-28","creditCardId":10,"amount":{"currency":"BRL","minorUnits":5000},"installments":3},
                  {"intent":"EXPENSE","date":"2026-06-28","accountId":1,"categoryId":404,"amount":{"currency":"BRL","minorUnits":1000}}
                ]}
            """),
        )

        val result = assertIs<ToolOutcome.Ok>(outcome).result
        val items = result["items"]!!.jsonArray.map { it.jsonObject }

        assertEquals(2, result["wouldBeRecordedCount"]!!.jsonPrimitive.content.toInt())
        assertEquals(1, result["wouldBeRefusedCount"]!!.jsonPrimitive.content.toInt())
        assertEquals(
            listOf("WOULD_BE_RECORDED", "WOULD_BE_RECORDED", "WOULD_BE_REFUSED"),
            items.map { it["status"]!!.jsonPrimitive.content },
        )
        assertEquals(
            WriteItemCodes.CATEGORY_NOT_FOUND,
            items[2]["error"]!!.jsonObject["code"]!!.jsonPrimitive.content,
        )
        assertTrue(createTransaction.forms.isEmpty(), "The dry run reached the write")
        assertTrue(addInstallment.calls.isEmpty(), "The dry run reached the write")
    }

    @Test
    fun `it says which bill a card purchase would fall in`() = runTest {
        val outcome = preview().execute(
            arguments("""
                {"items":[{"intent":"CARD_PURCHASE","date":"2026-06-28","creditCardId":10,
                           "amount":{"currency":"BRL","minorUnits":5000}}]}
            """),
        )

        val item = assertIs<ToolOutcome.Ok>(outcome).result["items"]!!.jsonArray.single().jsonObject
        val resolved = item["preview"]!!.jsonObject

        // The 28th is past the 10th, so it belongs to the cycle closing in July, due in July.
        assertEquals("2026-07", resolved["invoiceDueMonth"]!!.jsonPrimitive.content)
        assertEquals("Blue", resolved["creditCard"]!!.jsonObject["name"]!!.jsonPrimitive.content)
    }

    @Test
    fun `it warns about the same probable duplicate the write would warn about`() = runTest {
        val call = arguments("""
            {"items":[{"intent":"EXPENSE","date":"2026-06-28","accountId":1,"description":"Market",
                       "amount":{"currency":"BRL","minorUnits":1000}}]}
        """)

        val outcome = assertIs<ToolOutcome.Ok>(preview().execute(call))
        val item = outcome.result["items"]!!.jsonArray.single().jsonObject

        assertEquals(
            ToolWarningCode.PROBABLE_DUPLICATE.name,
            item["warnings"]!!.jsonArray.single().jsonObject["code"]!!.jsonPrimitive.content,
        )
        assertEquals(ToolWarningCode.PROBABLE_DUPLICATE, outcome.warnings.single().code)
    }

    @Test
    fun `the dry run and the write resolve the same invoice, from the same resolver`() = runTest {
        val recording = RecordingCreateTransaction { form ->
            Either.Right(
                transaction(
                    id = 1,
                    date = LocalDate(2026, 6, 28),
                    entries = listOf(entry(cardAccount, -5_000, julyBill.dimensionId), entry(expensesAccount, 5_000)),
                    title = form.title,
                ),
            )
        }
        val resolver = resolver(recording)
        val call = arguments("""
            {"items":[{"intent":"CARD_PURCHASE","date":"2026-06-28","creditCardId":10,
                       "amount":{"currency":"BRL","minorUnits":5000}}]}
        """)

        val previewed = assertIs<ToolOutcome.Ok>(
            PreviewTransactionsTool(resolver, duplicates()).execute(call),
        ).result["items"]!!.jsonArray.single().jsonObject["preview"]!!.jsonObject

        val clock = FixedClock(Instant.parse("2026-06-28T12:00:00Z"))
        RecordTransactionsTool(
            resolver = resolver,
            duplicates = duplicates(),
            idempotency = IdempotencyStore(clock),
            activity = ActivityRecorder(RecordingJournal(), clock),
        ).execute(call)

        assertEquals(julyBill.dueMonth, recording.forms.single().invoiceDueMonth)
        assertEquals(julyBill.dueMonth.toString(), previewed["invoiceDueMonth"]!!.jsonPrimitive.content)
    }

    @Test
    fun `it is annotated read-only, and takes no idempotency key`() {
        val tool = preview()

        assertEquals(true, tool.annotations.readOnlyHint)
        assertEquals(false, tool.annotations.destructiveHint)
        assertTrue(IDEMPOTENCY_KEY !in tool.inputSchema["properties"]!!.jsonObject.keys)
    }

    @Test
    fun `a call with no items is refused`() = runTest {
        val outcome = preview().execute(arguments("""{"items":[]}"""))

        assertEquals(BatchCodes.ITEMS_REQUIRED, assertIs<ToolOutcome.Failed>(outcome).error.code)
    }

    // ------------------------------------------------------------------ setup

    private val transactions = FakeTransactionRepository(listOf(existing))
    private val creditCards = FakeCreditCardRepository(listOf(card))

    private fun duplicates() = ProbableDuplicates(transactions, creditCards)

    private fun preview() = PreviewTransactionsTool(resolver(createTransaction), duplicates())

    private fun resolver(createTransaction: RecordingCreateTransaction): TransactionItemResolver {
        val accounts = FakeAccountRepository(listOf(checking, cardAccount))
        val invoices = FakeInvoiceRepository(listOf(julyBill))
        val entries = FakeEntryRepository()

        return TransactionItemResolver(
            accounts = accounts,
            creditCards = creditCards,
            categories = FakeCategoryRepository(listOf(groceries)),
            invoices = invoices,
            createTransaction = createTransaction,
            addInstallment = addInstallment,
            transferBetweenAccounts = TransferBetweenAccountsUseCase(
                transactionRepository = transactions,
                accountRepository = accounts,
                harvestExchangeRate = HarvestExchangeRateUseCase(FakeExchangeRates()),
            ),
            payInvoicePayment = RecordingPayInvoice { error("The dry run wrote something") },
            adjustBalance = AdjustBalanceUseCase(transactions, CalculateBalanceUseCase(entries)),
            adjustInvoice = AdjustInvoiceUseCase(transactions, CalculateInvoiceUseCase(entries)),
        )
    }

    private fun arguments(raw: String): JsonObject = Json.parseToJsonElement(raw.trimIndent()) as JsonObject
}
