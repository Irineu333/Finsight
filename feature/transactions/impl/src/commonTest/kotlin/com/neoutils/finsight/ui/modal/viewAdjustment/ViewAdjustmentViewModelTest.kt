@file:OptIn(ExperimentalCoroutinesApi::class)

package com.neoutils.finsight.ui.modal.viewAdjustment

import app.cash.turbine.test
import app.cash.turbine.turbineScope
import com.neoutils.finsight.domain.exception.DetailNotFoundException
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.extension.DisplayAmount.SignPolicy
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.view_transaction_leg_verb_adjusted
import com.neoutils.finsight.ui.model.TransactionFacades
import com.neoutils.finsight.ui.modal.FakeCrashlytics
import com.neoutils.finsight.ui.modal.FakeTransactionRepository
import com.neoutils.finsight.ui.modal.transaction
import com.neoutils.finsight.util.UiText
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
import kotlin.test.assertTrue

class ViewAdjustmentViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        repository: FakeTransactionRepository,
        crashlytics: FakeCrashlytics = FakeCrashlytics(),
    ) = ViewAdjustmentViewModel(
        transactionId = 1L,
        transactionRepository = repository,
        facadeResolver = { TransactionFacades() },
        crashlytics = crashlytics,
    )

    @Test
    fun loadingThenContent() = runTest(dispatcher) {
        val repository = FakeTransactionRepository()
        val vm = viewModel(repository)

        vm.uiState.test {
            assertEquals(ViewAdjustmentUiState.Loading, awaitItem())
            repository.emit(transaction(id = 1L, amount = 42.0, type = TransactionType.ADJUSTMENT))
            val leg = assertIs<ViewAdjustmentUiState.Content>(awaitItem()).legs().single()

            // The same card the transaction detail composes: the adjustment verb off
            // the `EQUITY` override, the account's own name, and the ledger's sign
            // spelled out, because "adjusted" withholds the direction.
            assertEquals(UiText.Res(Res.string.view_transaction_leg_verb_adjusted), leg.verb)
            assertEquals("Account", leg.name)
            assertEquals(SignPolicy.EXPLICIT_SIGN, leg.amount.policy)
            assertEquals(42.0, leg.amount.value)
        }
    }

    @Test
    fun firstEmissionNullShowsErrorAndRecordsException() = runTest(dispatcher) {
        val repository = FakeTransactionRepository()
        val crashlytics = FakeCrashlytics()
        val vm = viewModel(repository, crashlytics)

        vm.uiState.test {
            assertEquals(ViewAdjustmentUiState.Loading, awaitItem())
            repository.emit(null)
            assertEquals(ViewAdjustmentUiState.Error, awaitItem())
        }

        assertEquals(1, crashlytics.recorded.size)
        assertTrue(crashlytics.recorded.first() is DetailNotFoundException)
    }

    @Test
    fun deletionAfterContentEmitsDismiss() = runTest(dispatcher) {
        val repository = FakeTransactionRepository()
        val vm = viewModel(repository)

        turbineScope {
            val state = vm.uiState.testIn(backgroundScope)
            val events = vm.events.testIn(backgroundScope)

            assertEquals(ViewAdjustmentUiState.Loading, state.awaitItem())
            repository.emit(transaction(id = 1L, type = TransactionType.ADJUSTMENT))
            assertIs<ViewAdjustmentUiState.Content>(state.awaitItem())

            repository.emit(null)
            assertIs<ViewAdjustmentEvent.Dismiss>(events.awaitItem())
            state.expectNoEvents()

            state.cancel()
            events.cancel()
        }
    }

    private fun invoice() = Invoice(
        id = 1,
        creditCard = CreditCard(id = 1, name = "Card", limit = 1_000.0, closingDay = 1, dueDay = 10, accountId = 5),
        dimensionId = 7,
        openingMonth = YearMonth(2026, 1),
        closingMonth = YearMonth(2026, 2),
        dueMonth = YearMonth(2026, 2),
        status = Invoice.Status.OPEN,
    )

    /** An invoice adjustment as the ledger holds it: the card's leg against equity. */
    private fun invoiceAdjustment() = Transaction(
        id = 1L,
        title = null,
        date = LocalDate(2026, 1, 1),
        entries = listOf(
            Entry(
                account = Account(id = 5, name = "Card", type = AccountType.LIABILITY, currency = "BRL"),
                amount = -10_000,
                dimensionId = 7,
            ),
            Entry(
                account = Account(id = 12, name = "Reconciliation", type = AccountType.EQUITY, currency = "BRL"),
                amount = 10_000,
            ),
        ),
    )

    @Test
    fun aCardAdjustmentCarriesItsInvoiceInsideTheLiabilityCard() = runTest(dispatcher) {
        val repository = FakeTransactionRepository()
        val invoice = invoice()
        val vm = ViewAdjustmentViewModel(
            transactionId = 1L,
            transactionRepository = repository,
            facadeResolver = { TransactionFacades(creditCard = invoice.creditCard, invoice = invoice) },
            crashlytics = FakeCrashlytics(),
        )

        vm.uiState.test {
            assertEquals(ViewAdjustmentUiState.Loading, awaitItem())
            repository.emit(invoiceAdjustment())
            val leg = assertIs<ViewAdjustmentUiState.Content>(awaitItem()).legs().single()

            // The invoice is the dimension the liability leg carries, so it is stated
            // where that leg is stated — never as a sibling row of the operation.
            assertEquals(invoice.dueMonth, leg.invoice?.dueMonth)
            assertEquals(invoice.status, leg.invoice?.status)
        }
    }
}
