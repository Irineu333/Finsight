@file:OptIn(ExperimentalTime::class, ExperimentalCoroutinesApi::class)

package com.neoutils.finsight.ui.screen.invoiceTransactions

import app.cash.turbine.test
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.repository.DimensionFlowsByCurrency
import com.neoutils.finsight.domain.usecase.UnarchiveCreditCardUseCase
import com.neoutils.finsight.testing.FakeCardAccountRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.YearMonth
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * A created invoice lands in the middle of the pile, in calendar order — so the screen is
 * told the *month* and finds the page itself. Without that, creating would look like it did
 * nothing: the new invoice would sit off-screen behind the one the user was already on.
 */
class InvoiceTransactionsSelectionTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setup() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private val card = CreditCard(
        id = 1, name = "Card", limit = 1000.0, closingDay = 5, dueDay = 15, accountId = 10,
    )

    private fun invoice(id: Long, month: Int, status: Invoice.Status) = Invoice(
        id = id, creditCard = card, dimensionId = id,
        openingMonth = YearMonth(2026, month - 1),
        closingMonth = YearMonth(2026, month),
        dueMonth = YearMonth(2026, month),
        status = status,
    )

    // The order the pager renders: newest first, as the query returns them.
    private val invoices = listOf(
        invoice(id = 3, month = 5, status = Invoice.Status.FUTURE),
        invoice(id = 2, month = 4, status = Invoice.Status.OPEN),
        invoice(id = 1, month = 3, status = Invoice.Status.RETROACTIVE),
    )

    private fun viewModel() = InvoiceTransactionsViewModel(
        creditCardId = 1,
        creditCardRepository = FakeCreditCardRepository(card),
        accountRepository = FakeCardAccountRepository(),
        invoiceRepository = FakeInvoiceRepository(invoices),
        transactionRepository = FakeTransactionRepository(emptyList()),
        categoryRepository = FakeCategoryRepository(),
        installmentRepository = NoInstallments,
        entryRepository = FakeEntryRepository(
            owedByInvoiceId = invoices.associate { it.id to 0.0 },
            flowsByInvoiceId = invoices.associate { it.id to DimensionFlowsByCurrency.zero },
        ),
        recurringRepository = NoRecurring,
        unarchiveCreditCard = UnarchiveCreditCardUseCase(FakeCreditCardRepository(card)),
        crashlytics = NoCrashlytics,
        clock = Clock.System,
    )

    @Test
    fun `the pager goes to the invoice of the given due month`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.uiState.test {
            advanceUntilIdle()
            // It starts on the open invoice, at index 1.
            assertEquals(1, expectMostRecentItem().selectedInvoiceIndex)

            viewModel.onAction(
                InvoiceTransactionsAction.SelectInvoiceForDueMonth(YearMonth(2026, 3))
            )
            advanceUntilIdle()

            val state = expectMostRecentItem()
            assertEquals(2, state.selectedInvoiceIndex)
            assertEquals(
                YearMonth(2026, 3),
                state.invoices[state.selectedInvoiceIndex].dueMonth,
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a month with no invoice leaves the selection where it was`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.uiState.test {
            advanceUntilIdle()
            assertEquals(1, expectMostRecentItem().selectedInvoiceIndex)

            viewModel.onAction(
                InvoiceTransactionsAction.SelectInvoiceForDueMonth(YearMonth(2026, 12))
            )
            advanceUntilIdle()

            // The state does not move at all, so there is nothing new to emit.
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
