@file:OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)

package com.neoutils.finsight.ui.modal.editInvoiceBalance

import app.cash.turbine.test
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.analytics.Event
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.repository.IInvoiceRepository
import com.neoutils.finsight.domain.usecase.AdjustInvoiceUseCase
import com.neoutils.finsight.domain.usecase.CalculateInvoiceUseCase
import com.neoutils.finsight.domain.usecase.FakeEntryRepository
import com.neoutils.finsight.domain.usecase.FakeTransactionRepository
import com.neoutils.finsight.domain.usecase.InvoiceLedgerStore
import com.neoutils.finsight.ui.component.ModalManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * The date of an invoice adjustment says **when the correction happened**; the invoice says
 * where it settles. The two are independent axes: the value reaches the invoice through the
 * dimension, so nothing about it depends on the date — and that is exactly why the form has
 * to say when the two diverge.
 *
 * Today is 11 August 2026. The card closes on the 5th and falls due on the 15th, so the
 * invoice due in March 2026 admits purchases from 5 January to 5 February.
 */
class EditInvoiceBalanceViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setup() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private val today = LocalDate(2026, 8, 11)

    private val card = CreditCard(
        id = 1,
        name = "Card",
        limit = 1000.0,
        closingDay = 5,
        dueDay = 15,
        accountId = 10,
    )

    private val cardAccount = Account(
        id = 10,
        name = "Card",
        type = AccountType.LIABILITY,
        currency = "BRL",
    )

    /** Opens 5 January, closes 5 February, falls due in March. */
    private val januaryInvoice = Invoice(
        id = 1,
        creditCard = card,
        dimensionId = 1,
        openingMonth = YearMonth(2026, 1),
        closingMonth = YearMonth(2026, 2),
        dueMonth = YearMonth(2026, 3),
        status = Invoice.Status.OPEN,
    )

    /** Opens 5 June, closes 5 July, falls due in August. */
    private val juneInvoice = Invoice(
        id = 2,
        creditCard = card,
        dimensionId = 2,
        openingMonth = YearMonth(2026, 6),
        closingMonth = YearMonth(2026, 7),
        dueMonth = YearMonth(2026, 8),
        status = Invoice.Status.OPEN,
    )

    @Test
    fun `the sheet opens on today's day projected into the invoice's window`() = runTest(dispatcher) {
        val viewModel = viewModel(InvoiceLedgerStore(card), januaryInvoice)

        viewModel.uiState.test {
            advanceUntilIdle()
            val state = expectMostRecentItem() as EditInvoiceBalanceUiState.Content

            // The 11th belongs to the segment before the turn: 11 January.
            assertEquals("11/01/2026", state.date)
            assertFalse(state.isDateOutsideInvoice)
        }
    }

    /**
     * The other reading of the gesture: a correction made today, over an old cycle. It is
     * accepted, it counts fully in that invoice, and the divergence is said.
     */
    @Test
    fun `dating the correction today over an old invoice is accepted and flagged`() = runTest(dispatcher) {
        val ledger = InvoiceLedgerStore(card)
        val viewModel = viewModel(ledger, januaryInvoice)

        viewModel.uiState.test {
            advanceUntilIdle()

            viewModel.onAction(EditInvoiceBalanceAction.ChangeDate("11/08/2026"))
            advanceUntilIdle()

            val state = expectMostRecentItem() as EditInvoiceBalanceUiState.Content
            assertTrue(state.isDateOutsideInvoice)

            viewModel.onAction(EditInvoiceBalanceAction.Submit(targetBalance = 300.0))
            advanceUntilIdle()

            assertEquals(
                mapOf(LocalDate(2026, 8, 11) to 300.0),
                ledger.adjustmentsByDate(januaryInvoice.dimensionId!!),
            )
            assertEquals(300.0, ledger.dimensionOwed(januaryInvoice.dimensionId!!))
        }
    }

    @Test
    fun `a date inside the window signals nothing`() = runTest(dispatcher) {
        val viewModel = viewModel(InvoiceLedgerStore(card), januaryInvoice)

        viewModel.uiState.test {
            advanceUntilIdle()

            viewModel.onAction(EditInvoiceBalanceAction.ChangeDate("20/01/2026"))
            advanceUntilIdle()

            assertFalse((expectMostRecentItem() as EditInvoiceBalanceUiState.Content).isDateOutsideInvoice)
        }
    }

    /** The window is no floor: an adjustment happens over the cycle, not inside it. */
    @Test
    fun `a date earlier than the invoice's opening is accepted as written`() = runTest(dispatcher) {
        val ledger = InvoiceLedgerStore(card)
        val viewModel = viewModel(ledger, januaryInvoice)

        viewModel.uiState.test {
            advanceUntilIdle()

            viewModel.onAction(EditInvoiceBalanceAction.ChangeDate("02/12/2025"))
            advanceUntilIdle()
            viewModel.onAction(EditInvoiceBalanceAction.Submit(targetBalance = 120.0))
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()

            assertEquals(
                mapOf(LocalDate(2025, 12, 2) to 120.0),
                ledger.adjustmentsByDate(januaryInvoice.dimensionId!!),
            )
        }
    }

    @Test
    fun `switching invoices reprojects the date into the new window keeping the day`() =
        runTest(dispatcher) {
            val viewModel = viewModel(InvoiceLedgerStore(card), januaryInvoice)

            viewModel.uiState.test {
                advanceUntilIdle()
                assertEquals(
                    "11/01/2026",
                    (expectMostRecentItem() as EditInvoiceBalanceUiState.Content).date,
                )

                viewModel.onAction(EditInvoiceBalanceAction.SelectInvoice(juneInvoice))
                advanceUntilIdle()

                // 5 June–5 July: the 11th is past the closing day, so it belongs to June.
                val state = expectMostRecentItem() as EditInvoiceBalanceUiState.Content
                assertEquals("11/06/2026", state.date)
                assertFalse(state.isDateOutsideInvoice)
            }
        }

    /** The value reaches the invoice through the dimension, so the date decides none of it. */
    @Test
    fun `changing only the date leaves the amount owed where it was`() = runTest(dispatcher) {
        val ledger = InvoiceLedgerStore(card)
        val viewModel = viewModel(ledger, januaryInvoice)

        viewModel.uiState.test {
            advanceUntilIdle()
            viewModel.onAction(EditInvoiceBalanceAction.Submit(targetBalance = 300.0))
            advanceUntilIdle()

            val owed = ledger.dimensionOwed(januaryInvoice.dimensionId!!)

            viewModel.onAction(EditInvoiceBalanceAction.ChangeDate("11/08/2026"))
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()

            assertEquals(300.0, owed)
            assertEquals(owed, ledger.dimensionOwed(januaryInvoice.dimensionId!!))
        }
    }

    /**
     * An adjustment is not a purchase, so the invoice's closing is no ceiling for it — only
     * today is.
     */
    @Test
    fun `an already-closed invoice takes an adjustment dated after its closing`() = runTest(dispatcher) {
        val closed = januaryInvoice.copy(status = Invoice.Status.CLOSED)
        val ledger = InvoiceLedgerStore(card)
        val viewModel = viewModel(ledger, closed)

        viewModel.uiState.test {
            advanceUntilIdle()

            viewModel.onAction(EditInvoiceBalanceAction.ChangeDate("11/08/2026"))
            advanceUntilIdle()
            viewModel.onAction(EditInvoiceBalanceAction.Submit(targetBalance = 90.0))
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()

            assertEquals(
                mapOf(LocalDate(2026, 8, 11) to 90.0),
                ledger.adjustmentsByDate(closed.dimensionId!!),
            )
        }
    }

    private fun viewModel(ledger: InvoiceLedgerStore, invoice: Invoice) = EditInvoiceBalanceViewModel(
        initialInvoice = invoice,
        adjustInvoiceUseCase = AdjustInvoiceUseCase(
            transactionRepository = FakeTransactionRepository(ledger),
            calculateInvoiceUseCase = CalculateInvoiceUseCase(FakeEntryRepository(ledger)),
        ),
        calculateInvoiceUseCase = CalculateInvoiceUseCase(FakeEntryRepository(ledger)),
        invoiceRepository = TwoInvoices(januaryInvoice, juneInvoice),
        creditCardRepository = OneCard(card),
        accountRepository = CardAccounts(cardAccount),
        modalManager = ModalManager(),
        analytics = MuteAnalytics,
        crashlytics = MuteCrashlytics,
        clock = ClockOn(today),
    )
}

private class ClockOn(private val today: LocalDate) : Clock {
    override fun now(): Instant = today.atStartOfDayIn(TimeZone.currentSystemDefault())
}

private object MuteAnalytics : Analytics {
    override fun logScreenView(screenName: String) = Unit
    override fun logEvent(event: Event) = Unit
    override fun setUserId(id: String?) = Unit
}

private object MuteCrashlytics : Crashlytics {
    override fun setUserId(id: String?) = Unit
    override fun recordException(e: Throwable) = Unit
}

private class OneCard(private val card: CreditCard) : ICreditCardRepository {
    override fun observeAllCreditCards(): Flow<List<CreditCard>> = flowOf(listOf(card))
    override suspend fun getAllCreditCards(): List<CreditCard> = listOf(card)
    override fun observeAllCreditCardsIncludingClosed(): Flow<List<CreditCard>> = throw NotImplementedError()
    override fun observeCreditCardById(creditCardId: Long): Flow<CreditCard?> = throw NotImplementedError()
    override suspend fun getAllCreditCardsIncludingClosed(): List<CreditCard> = throw NotImplementedError()
    override suspend fun getCreditCardById(creditCardId: Long): CreditCard? = throw NotImplementedError()
    override suspend fun insert(creditCard: CreditCard, currency: String): Long = throw NotImplementedError()
    override suspend fun currencyForNewCard(): String = throw NotImplementedError()
    override suspend fun update(creditCard: CreditCard) = throw NotImplementedError()
    override suspend fun delete(creditCard: CreditCard) = throw NotImplementedError()
    override suspend fun unarchive(accountId: Long) = throw NotImplementedError()
}

private class TwoInvoices(private vararg val invoices: Invoice) : IInvoiceRepository {
    override suspend fun getInvoicesByCreditCard(creditCardId: Long) =
        invoices.filter { it.creditCard.id == creditCardId }

    override fun observeAllInvoices(): Flow<List<Invoice>> = throw NotImplementedError()
    override fun observeInvoicesByCreditCard(creditCardId: Long): Flow<List<Invoice>> = throw NotImplementedError()
    override fun observeInvoiceById(invoiceId: Long): Flow<Invoice?> = throw NotImplementedError()
    override fun observeOpenInvoice(creditCardId: Long): Flow<Invoice?> = throw NotImplementedError()
    override fun observeAvailableInvoices(creditCardId: Long): Flow<List<Invoice>> = throw NotImplementedError()
    override fun observeUnpaidInvoice(creditCardId: Long): Flow<Invoice?> = throw NotImplementedError()
    override fun observeUnpaidInvoices(): Flow<List<Invoice>> = throw NotImplementedError()
    override fun observeInvoicesToSettle(month: YearMonth): Flow<List<Invoice>> = throw NotImplementedError()
    override suspend fun getAllInvoices(): List<Invoice> = throw NotImplementedError()
    override suspend fun getUnpaidInvoicesByCreditCard(creditCardId: Long): List<Invoice> = throw NotImplementedError()
    override suspend fun getOpenInvoice(creditCardId: Long): Invoice? = invoices.firstOrNull()
    override suspend fun getInvoiceById(id: Long): Invoice? = invoices.firstOrNull { it.id == id }
    override suspend fun insert(invoice: Invoice): Invoice = throw NotImplementedError()
    override suspend fun update(invoice: Invoice) = throw NotImplementedError()
    override suspend fun deleteById(id: Long) = throw NotImplementedError()
}

private class CardAccounts(private val account: Account) : IAccountRepository {
    override suspend fun getAccountById(accountId: Long): Account? = account.takeIf { it.id == accountId }
    override fun observeAllAccounts(): Flow<List<Account>> = flowOf(listOf(account))
    override fun observeAllAccountsIncludingClosed(): Flow<List<Account>> = throw NotImplementedError()
    override fun observeAllLedgerAccounts(): Flow<List<Account>> = throw NotImplementedError()
    override fun observeAccountById(accountId: Long): Flow<Account?> = throw NotImplementedError()
    override fun observeDefaultAccount(): Flow<Account?> = throw NotImplementedError()
    override suspend fun getAllAccounts(): List<Account> = throw NotImplementedError()
    override suspend fun getAllAccountsIncludingClosed(): List<Account> = throw NotImplementedError()
    override suspend fun getAllLedgerAccounts(): List<Account> = throw NotImplementedError()
    override suspend fun getDefaultAccount(): Account? = throw NotImplementedError()
    override suspend fun getAccountCount(): Int = throw NotImplementedError()
    override suspend fun hasYieldingAccount(): Boolean = throw NotImplementedError()
    override suspend fun insert(account: Account): Long = throw NotImplementedError()
    override suspend fun update(account: Account) = throw NotImplementedError()
    override suspend fun delete(account: Account) = throw NotImplementedError()
    override suspend fun reopen(accountId: Long) = throw NotImplementedError()
}
