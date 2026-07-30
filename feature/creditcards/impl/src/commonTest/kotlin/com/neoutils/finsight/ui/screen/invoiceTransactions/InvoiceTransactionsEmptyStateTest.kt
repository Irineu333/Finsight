@file:OptIn(ExperimentalCoroutinesApi::class)

package com.neoutils.finsight.ui.screen.invoiceTransactions

import app.cash.turbine.test
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.repository.DimensionFlows
import com.neoutils.finsight.domain.usecase.UnarchiveCreditCardUseCase
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

/**
 * The two emptinesses of the invoice list, and the loading state that used to look
 * exactly like the first one.
 */
class InvoiceTransactionsEmptyStateTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setup() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private val card = CreditCard(id = 1, name = "Card", limit = 1000.0, closingDay = 5, dueDay = 15)

    private fun invoice(id: Long, dimensionId: Long, month: Int) = Invoice(
        id = id, creditCard = card, dimensionId = dimensionId,
        openingMonth = YearMonth(2026, month),
        closingMonth = YearMonth(2026, month + 1),
        dueMonth = YearMonth(2026, month + 2),
        status = Invoice.Status.OPEN,
    )

    private val cardAccount = Account(id = 10, name = "Card", type = AccountType.LIABILITY, currency = "BRL")
    private val expenseAccount = Account(id = 20, name = "Expense", type = AccountType.EXPENSE, currency = "BRL")

    private fun purchase(id: Long, dimensionId: Long) = Transaction(
        id = id,
        title = "Purchase",
        date = LocalDate(2026, 3, 10),
        entries = listOf(
            Entry(transactionId = id, account = cardAccount, amount = -6_000, dimensionId = dimensionId),
            Entry(transactionId = id, account = expenseAccount, amount = 6_000, dimensionId = 77),
        ),
    )

    private fun viewModel(
        invoices: List<Invoice>,
        transactions: List<Transaction>,
    ) = InvoiceTransactionsViewModel(
        creditCardId = 1,
        creditCardRepository = FakeCreditCardRepository(card),
        invoiceRepository = FakeInvoiceRepository(invoices),
        transactionRepository = FakeTransactionRepository(transactions),
        categoryRepository = FakeCategoryRepository(),
        installmentRepository = NoInstallments,
        entryRepository = FakeEntryRepository(
            owedByInvoiceId = emptyMap(),
            flowsByInvoiceId = invoices.associate { it.id to DimensionFlows(0.0, 0.0, 0.0) },
        ),
        recurringRepository = NoRecurring,
        unarchiveCreditCard = UnarchiveCreditCardUseCase(FakeCreditCardRepository(card)),
        crashlytics = NoCrashlytics,
    )

    @Test
    fun `the screen asserts nothing before the first read`() = runTest(dispatcher) {
        val vm = viewModel(invoices = listOf(invoice(1, dimensionId = 1, month = 2)), transactions = emptyList())

        vm.uiState.test {
            assertEquals(
                expected = ListState.Loading,
                actual = awaitItem().listState,
                message = "the initial value must not pass for an empty invoice",
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an invoice with nothing on it reads as an empty invoice`() = runTest(dispatcher) {
        val vm = viewModel(invoices = listOf(invoice(1, dimensionId = 1, month = 2)), transactions = emptyList())

        vm.uiState.test {
            assertEquals(ListState.EmptyInvoice, awaitListState { it != ListState.Loading })
        }
    }

    @Test
    fun `a filter that cuts everything offers to clear`() = runTest(dispatcher) {
        val vm = viewModel(
            invoices = listOf(invoice(1, dimensionId = 1, month = 2)),
            transactions = listOf(purchase(id = 1, dimensionId = 1)),
        )

        vm.uiState.test {
            assertIs<ListState.Content>(awaitListState { it is ListState.Content })

            vm.onAction(InvoiceTransactionsAction.ToggleInstallment(enabled = true))

            val listState = assertIs<ListState.EmptyScope>(awaitListState { it is ListState.EmptyScope })
            assertEquals(true, listState.canClearFilters)

            vm.onAction(InvoiceTransactionsAction.ClearFilters)

            assertIs<ListState.Content>(awaitListState { it is ListState.Content })
        }
    }

    @Test
    fun `switching invoice does not go back to loading`() = runTest(dispatcher) {
        val vm = viewModel(
            invoices = listOf(
                invoice(1, dimensionId = 1, month = 2),
                invoice(2, dimensionId = 2, month = 3),
            ),
            transactions = listOf(purchase(id = 1, dimensionId = 2)),
        )

        vm.uiState.test {
            awaitListState { it != ListState.Loading }

            vm.onAction(InvoiceTransactionsAction.SelectInvoice(1))

            var state = awaitItem()
            while (state.selectedInvoiceIndex != 1) {
                assertEquals(false, state.listState == ListState.Loading, "loading is a starting state only")
                state = awaitItem()
            }
            assertIs<ListState.Content>(state.listState)
        }
    }
}

private suspend fun app.cash.turbine.TurbineTestContext<InvoiceTransactionsUiState>.awaitListState(
    predicate: (ListState) -> Boolean,
): ListState {
    var state = awaitItem().listState
    while (!predicate(state)) state = awaitItem().listState
    return state
}
