@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.mcp.write

import arrow.core.Either
import com.neoutils.finsight.domain.error.TransferError
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.TransactionLabel
import com.neoutils.finsight.domain.model.TransactionTarget
import com.neoutils.finsight.domain.model.TransactionType
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
import com.neoutils.finsight.mcp.RecordingAddInstallment
import com.neoutils.finsight.mcp.RecordingCreateTransaction
import com.neoutils.finsight.mcp.RecordingPayInvoice
import com.neoutils.finsight.mcp.account
import com.neoutils.finsight.mcp.category
import com.neoutils.finsight.mcp.creditCard
import com.neoutils.finsight.mcp.entry
import com.neoutils.finsight.mcp.expensesAccount
import com.neoutils.finsight.mcp.invoice
import com.neoutils.finsight.mcp.transaction
import com.neoutils.finsight.mcp.contract.ToolErrorCategory
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime

class TransactionItemResolverTest {

    private val checking = account(1, "Checking", currency = "BRL", isDefault = true)
    private val savings = account(2, "Savings", currency = "USD")
    // A card *is* its `LIABILITY` account in the chart — that is what the invoice leg posts to.
    private val cardAccount = account(3, "Card", currency = "BRL", type = AccountType.LIABILITY)
    private val card = creditCard(10, "Blue", accountId = cardAccount.id, closingDay = 10, dueDay = 20)
    private val groceries = category(20, "Groceries", type = Category.Type.EXPENSE)
    private val salary = category(21, "Salary", type = Category.Type.INCOME)
    private val julyBill = invoice(30, card, dueMonth = YearMonth(2026, 7), status = Invoice.Status.OPEN)

    private val createTransaction = RecordingCreateTransaction { form ->
        Either.Right(
            transaction(
                id = 1,
                date = LocalDate(2026, 6, 28),
                entries = listOf(entry(checking, -1_000), entry(expensesAccount, 1_000, groceries.dimensionId)),
                title = form.title,
            ),
        )
    }

    private val addInstallment = RecordingAddInstallment { _, count ->
        Either.Right(
            List(count) { index ->
                transaction(
                    id = (index + 1).toLong(),
                    date = LocalDate(2026, 6, 28),
                    entries = listOf(entry(cardAccount, -1_000, julyBill.dimensionId), entry(expensesAccount, 1_000)),
                    installmentId = 7,
                    installmentNumber = index + 1,
                )
            },
        )
    }

    private val payInvoice = RecordingPayInvoice { Either.Right(julyBill) }

    // ------------------------------------------------------------------ shape

    @Test
    fun `an expense becomes a call of the use case that owns it`() = runTest {
        val resolved = resolver().resolve(
            item("""{"intent":"EXPENSE","date":"2026-06-28","accountId":1,"categoryId":20,
                     "description":"Market","amount":{"currency":"BRL","minorUnits":1000}}"""),
        ).right()

        val transactions = resolved.apply().right()

        assertEquals(1, transactions.size)
        val form = createTransaction.forms.single()
        assertEquals(TransactionType.EXPENSE, form.type)
        assertEquals(TransactionTarget.ACCOUNT, form.target)
        assertEquals(checking, form.account)
        assertEquals(groceries, form.category)
        assertEquals("1000", form.amount)
        assertEquals("28/06/2026", form.date)
    }

    @Test
    fun `the label comes back derived, and no item can declare one`() = runTest {
        val resolved = resolver().resolve(
            item("""{"intent":"EXPENSE","date":"2026-06-28","accountId":1,"categoryId":20,
                     "amount":{"currency":"BRL","minorUnits":1000},"label":"INCOME"}"""),
        ).right()

        assertEquals(TransactionLabel.EXPENSE, resolved.apply().right().single().label)
        assertNull(resolved.preview["label"])
    }

    // ------------------------------------------------------- opaque identifiers

    @Test
    fun `a name where an identifier belongs is refused, never matched`() = runTest {
        val error = resolver().resolve(
            item("""{"intent":"EXPENSE","date":"2026-06-28","accountId":"Checking",
                     "amount":{"currency":"BRL","minorUnits":1000}}"""),
        ).left()

        assertEquals(WriteItemCodes.NAME_IS_NOT_AN_IDENTIFIER, error.code)
        assertEquals(ToolErrorCategory.INVALID_INPUT, error.category)
    }

    @Test
    fun `a category that does not exist is invalid input naming it, and none is created`() = runTest {
        val error = resolver().resolve(
            item("""{"intent":"EXPENSE","date":"2026-06-28","accountId":1,"categoryId":999,
                     "amount":{"currency":"BRL","minorUnits":1000}}"""),
        ).left()

        assertEquals(ToolErrorCategory.INVALID_INPUT, error.category)
        assertEquals(WriteItemCodes.CATEGORY_NOT_FOUND, error.code)
        assertContains(error.message, "999")
        assertTrue(createTransaction.forms.isEmpty(), "Nothing was written")
    }

    @Test
    fun `a system account of the ledger is not reachable as a parameter`() = runTest {
        // The chart holds it and the account facade does not, which is exactly the
        // distinction: it is mechanism, it appears in no listing, and it is not a
        // destination for the user's money.
        val error = resolver().resolve(
            item("""{"intent":"EXPENSE","date":"2026-06-28","accountId":${expensesAccount.id},
                     "amount":{"currency":"BRL","minorUnits":1000}}"""),
        ).left()

        assertEquals(WriteItemCodes.ACCOUNT_NOT_FOUND, error.code)
    }

    @Test
    fun `an income category does not classify an expense`() = runTest {
        val error = resolver().resolve(
            item("""{"intent":"EXPENSE","date":"2026-06-28","accountId":1,"categoryId":21,
                     "amount":{"currency":"BRL","minorUnits":1000}}"""),
        ).left()

        assertEquals(WriteItemCodes.CATEGORY_TYPE_MISMATCH, error.code)
    }

    @Test
    fun `an amount in another currency than the account's is refused`() = runTest {
        val error = resolver().resolve(
            item("""{"intent":"EXPENSE","date":"2026-06-28","accountId":1,
                     "amount":{"currency":"USD","minorUnits":1000}}"""),
        ).left()

        assertEquals(WriteItemCodes.CURRENCY_MISMATCH, error.code)
    }

    @Test
    fun `a bare number is not money on this boundary`() = runTest {
        val error = resolver().resolve(
            item("""{"intent":"EXPENSE","date":"2026-06-28","accountId":1,"amount":10.0}"""),
        ).left()

        assertEquals(WriteItemCodes.MISSING_FIELD, error.code)
    }

    @Test
    fun `a period in natural language is not a date`() = runTest {
        val error = resolver().resolve(
            item("""{"intent":"EXPENSE","date":"yesterday","accountId":1,
                     "amount":{"currency":"BRL","minorUnits":1000}}"""),
        ).left()

        assertEquals("NOT_A_CIVIL_DATE", error.code)
    }

    // ------------------------------------------------------------- card purchase

    @Test
    fun `which bill a purchase falls in is read off the card's own cycle`() = runTest {
        // The card closes on the 10th and falls due on the 20th, so its due month is the
        // month the cycle closes in: a purchase on 28 June belongs to the cycle closing in
        // July, which is billed in July.
        val resolved = resolver().resolve(
            item("""{"intent":"CARD_PURCHASE","date":"2026-06-28","creditCardId":10,"categoryId":20,
                     "amount":{"currency":"BRL","minorUnits":5000}}"""),
        ).right()

        assertEquals("2026-07", resolved.preview["invoiceDueMonth"]!!.jsonPrimitive.content)

        resolved.apply().right()
        assertEquals(YearMonth(2026, 7), createTransaction.forms.single().invoiceDueMonth)
    }

    @Test
    fun `a bill named outright is the month, and it is not recomputed`() = runTest {
        val resolved = resolver().resolve(
            item("""{"intent":"CARD_PURCHASE","date":"2026-02-03","creditCardId":10,"invoiceId":30,
                     "categoryId":20,"amount":{"currency":"BRL","minorUnits":5000}}"""),
        ).right()

        assertEquals("2026-07", resolved.preview["invoiceDueMonth"]!!.jsonPrimitive.content)
    }

    @Test
    fun `an instalment plan is one operation, never one call per payment`() = runTest {
        val resolved = resolver().resolve(
            item("""{"intent":"CARD_PURCHASE","date":"2026-06-28","creditCardId":10,"categoryId":20,
                     "installments":12,"amount":{"currency":"BRL","minorUnits":120000}}"""),
        ).right()

        val written = resolved.apply().right()

        assertEquals(12, written.size)
        assertEquals(1, addInstallment.calls.size)
        assertEquals(12, addInstallment.calls.single().second)
        assertTrue(createTransaction.forms.isEmpty(), "The single-payment path was not taken")
    }

    // ------------------------------------------------------------------ transfer

    @Test
    fun `a transfer across currencies states both ends and accepts no rate`() = runTest {
        val resolved = resolver().resolve(
            item("""{"intent":"TRANSFER","date":"2026-06-28","accountId":1,"destinationAccountId":2,
                     "amount":{"currency":"BRL","minorUnits":55000},
                     "destinationAmount":{"currency":"USD","minorUnits":10000},
                     "rate":5.5}"""),
        ).right()

        assertEquals(55_000, resolved.preview["amount"]!!.jsonObject["minorUnits"]!!.jsonPrimitive.long())
        assertEquals(10_000, resolved.preview["destinationAmount"]!!.jsonObject["minorUnits"]!!.jsonPrimitive.long())
        assertNull(resolved.preview["rate"], "No rate is ever a parameter on this path")
    }

    @Test
    fun `a refusal of the domain comes back as a rule refusal that is not retryable`() = runTest {
        // Source and destination are the same account, which the transfer use case
        // refuses by a rule of its own — the refusal this surface has to carry back.
        val resolved = resolver().resolve(
            item("""{"intent":"TRANSFER","date":"2026-06-28","accountId":1,"destinationAccountId":1,
                     "amount":{"currency":"BRL","minorUnits":1000}}"""),
        ).right()

        val error = resolved.apply().left()
        assertEquals(ToolErrorCategory.DOMAIN_RULE, error.category)
        assertEquals(DomainRefusalCodes.TRANSFER, error.code)
        assertEquals(false, error.isRetryable)
        assertEquals(TransferError.SameAccount.message, error.message)
    }

    // ----------------------------------------------------------------- payment

    @Test
    fun `paying a bill from another currency states what leaves the account`() = runTest {
        resolver().resolve(
            item("""{"intent":"INVOICE_PAYMENT","date":"2026-07-20","invoiceId":30,"accountId":2,
                     "amount":{"currency":"USD","minorUnits":10000}}"""),
        ).right().apply().right()

        assertEquals(Triple(30L, LocalDate(2026, 7, 20), 100.0), payInvoice.calls.single())
    }

    // -------------------------------------------------------------- adjustments

    @Test
    fun `an account adjustment states the balance it should read, signed`() = runTest {
        val resolved = resolver().resolve(
            item("""{"intent":"ACCOUNT_ADJUSTMENT","date":"2026-06-28","accountId":1,
                     "targetBalance":{"currency":"BRL","minorUnits":-2500}}"""),
        ).right()

        assertEquals(WriteIntent.ACCOUNT_ADJUSTMENT, resolved.intent)
        assertEquals(-2_500, resolved.preview["targetBalance"]!!.jsonObject["minorUnits"]!!.jsonPrimitive.long())
    }

    @Test
    fun `an unknown intent is refused, naming what this server writes`() = runTest {
        val error = resolver().resolve(item("""{"intent":"CLOSE_INVOICE","date":"2026-06-28"}""")).left()

        assertEquals(WriteItemCodes.UNKNOWN_INTENT, error.code)
        assertContains(error.message, "CARD_PURCHASE")
    }

    // ------------------------------------------------------------------- wiring

    private fun resolver(): TransactionItemResolver {
        val entries = FakeEntryRepository()
        val transactions = FakeTransactionRepository()
        val accounts = FakeAccountRepository(
            userAccounts = listOf(checking, savings),
            chart = listOf(checking, savings, cardAccount, expensesAccount),
        )

        return TransactionItemResolver(
            accounts = accounts,
            creditCards = FakeCreditCardRepository(listOf(card)),
            categories = FakeCategoryRepository(listOf(groceries, salary)),
            invoices = FakeInvoiceRepository(listOf(julyBill)),
            createTransaction = createTransaction,
            addInstallment = addInstallment,
            // The real use case over fake repositories: its refusals are the ones this
            // surface has to carry back, and a stub would only assert the stub.
            transferBetweenAccounts = TransferBetweenAccountsUseCase(
                transactionRepository = transactions,
                accountRepository = accounts,
                harvestExchangeRate = HarvestExchangeRateUseCase(FakeExchangeRates()),
            ),
            payInvoicePayment = payInvoice,
            adjustBalance = AdjustBalanceUseCase(transactions, CalculateBalanceUseCase(entries)),
            adjustInvoice = AdjustInvoiceUseCase(transactions, CalculateInvoiceUseCase(entries)),
        )
    }

    private fun item(raw: String): JsonObject = Json.parseToJsonElement(raw) as JsonObject
}

private fun <T> Either<*, T>.right(): T = fold(
    ifLeft = { error("Expected a resolution, got $it") },
    ifRight = { it },
)

private fun <L> Either<L, *>.left(): L = fold(
    ifLeft = { it },
    ifRight = { error("Expected a refusal, got $it") },
)

private fun kotlinx.serialization.json.JsonPrimitive.long(): Long = content.toLong()
