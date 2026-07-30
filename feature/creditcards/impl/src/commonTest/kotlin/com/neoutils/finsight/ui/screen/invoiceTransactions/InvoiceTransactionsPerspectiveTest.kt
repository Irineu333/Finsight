@file:OptIn(ExperimentalCoroutinesApi::class)

package com.neoutils.finsight.ui.screen.invoiceTransactions

import app.cash.turbine.test
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.domain.repository.DimensionFlowsByCurrency
import com.neoutils.finsight.domain.usecase.UnarchiveCreditCardUseCase
import com.neoutils.finsight.ui.model.toTransactionUi
import com.neoutils.finsight.ui.screen.invoiceTransactions.InvoiceTransactionsUiState.ListState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import com.neoutils.finsight.testing.FakeCardAccountRepository

/**
 * This screen shows one card, so it reads a transaction through the card's own leg.
 *
 * An invoice payment is the only form where that matters: it is the one transaction here
 * with two monetary legs, and the leg picked without a perspective is the *account's* — the
 * money leaving the wallet — rather than the card's. From the card, the same payment is
 * money coming in.
 *
 * The pairing is the point. The filter used to reach for the `LIABILITY` leg by hand while
 * the item beside it was mapped with no perspective at all, so filtering by income returned
 * a payment the list then presented as an expense. Both halves now come from
 * `Transaction.legUnder`, and this test fails if either drifts.
 */
class InvoiceTransactionsPerspectiveTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setup() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private val card = CreditCard(
        id = 1, name = "Card", limit = 1000.0, closingDay = 5, dueDay = 15, accountId = 10,
    )

    private val cardAccount = Account(id = 10, name = "Card", type = AccountType.LIABILITY, currency = "BRL")
    private val checking = Account(id = 30, name = "Checking", type = AccountType.ASSET, currency = "BRL")
    private val expenseAccount = Account(id = 20, name = "Expense", type = AccountType.EXPENSE, currency = "BRL")

    private val invoice = Invoice(
        id = 1, creditCard = card, dimensionId = 1,
        openingMonth = YearMonth(2026, 3),
        closingMonth = YearMonth(2026, 4),
        dueMonth = YearMonth(2026, 5),
        status = Invoice.Status.OPEN,
    )

    private val purchase = Transaction(
        id = 1, title = "Purchase", date = LocalDate(2026, 3, 10),
        entries = listOf(
            Entry(transactionId = 1, account = cardAccount, amount = -6_000, dimensionId = 1),
            Entry(transactionId = 1, account = expenseAccount, amount = 6_000, dimensionId = 77),
        ),
    )

    // Money leaves the account and lands on the card: two monetary legs, opposite readings.
    private val payment = Transaction(
        id = 2, title = "Payment", date = LocalDate(2026, 3, 20),
        entries = listOf(
            Entry(transactionId = 2, account = checking, amount = -6_000),
            Entry(transactionId = 2, account = cardAccount, amount = 6_000, dimensionId = 1),
        ),
    )

    private fun viewModel() = InvoiceTransactionsViewModel(
        creditCardId = 1,
        creditCardRepository = FakeCreditCardRepository(card),
        accountRepository = FakeCardAccountRepository(),
        invoiceRepository = FakeInvoiceRepository(listOf(invoice)),
        transactionRepository = FakeTransactionRepository(listOf(purchase, payment)),
        categoryRepository = FakeCategoryRepository(),
        installmentRepository = NoInstallments,
        entryRepository = FakeEntryRepository(
            owedByInvoiceId = mapOf(1L to 0.0),
            flowsByInvoiceId = mapOf(1L to DimensionFlowsByCurrency.zero),
        ),
        recurringRepository = NoRecurring,
        unarchiveCreditCard = UnarchiveCreditCardUseCase(FakeCreditCardRepository(card)),
        crashlytics = NoCrashlytics,
    )

    private suspend fun settledState(
        vararg actions: InvoiceTransactionsAction,
    ): InvoiceTransactionsUiState {
        val vm = viewModel()
        var result = InvoiceTransactionsUiState()
        vm.uiState.test {
            var state = awaitItem()
            while (state.listState is ListState.Loading) state = awaitItem()

            actions.forEach { action ->
                vm.onAction(action)
                state = awaitItem()
            }

            result = state
            cancelAndIgnoreRemainingEvents()
        }
        return result
    }

    @Test
    fun `the screen declares the card's account as its perspective`() = runTest(dispatcher) {
        assertEquals(card.accountId, settledState().cardAccountId)
    }

    @Test
    fun `a payment read through the card is money coming in`() = runTest(dispatcher) {
        val perspective = settledState().cardAccountId

        assertEquals(TransactionType.INCOME, payment.toTransactionUi(perspective)?.direction)
        // Without one, the same payment reads through the account's leg — which is exactly
        // what this screen used to do.
        assertEquals(TransactionType.EXPENSE, payment.toTransactionUi()?.direction)
    }

    @Test
    fun `the type filter agrees with the item it returns`() = runTest(dispatcher) {
        val listed = listedUnder(TransactionType.INCOME)

        // The state carries the item the screen renders, so the two halves are compared
        // where they meet rather than re-derived by the test.
        assertEquals(listOf(payment.id), listed.map { it.id }, "filtering by income returns the payment")
        assertEquals(TransactionType.INCOME, listed.single().direction, "and the item agrees")
    }

    @Test
    fun `a purchase is unaffected, having a single monetary leg`() = runTest(dispatcher) {
        val listed = listedUnder(TransactionType.EXPENSE)

        assertEquals(listOf(purchase.id), listed.map { it.id })
        assertEquals(
            purchase.toTransactionUi()?.direction,
            listed.single().direction,
            "one monetary leg, so the perspective changes nothing",
        )
    }

    private suspend fun listedUnder(type: TransactionType) = assertIs<ListState.Content>(
        settledState(InvoiceTransactionsAction.SelectType(type)).listState
    ).transactions.values.flatten()
}
