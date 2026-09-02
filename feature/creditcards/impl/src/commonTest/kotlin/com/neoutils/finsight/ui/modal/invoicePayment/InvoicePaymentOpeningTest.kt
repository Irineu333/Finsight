@file:OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)

package com.neoutils.finsight.ui.modal.invoicePayment

import app.cash.turbine.test
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.analytics.Event
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.MoneyByCurrency
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.repository.IInvoiceRepository
import com.neoutils.finsight.domain.usecase.AdvanceInvoicePaymentUseCase
import com.neoutils.finsight.domain.usecase.AdvanceInvoicePaymentUseCaseImpl
import com.neoutils.finsight.domain.usecase.CalculateInvoiceUseCase
import com.neoutils.finsight.domain.usecase.CalculateInvoiceUseCaseImpl
import com.neoutils.finsight.domain.usecase.HarvestExchangeRateUseCase
import com.neoutils.finsight.domain.usecase.PayInvoicePaymentUseCase
import com.neoutils.finsight.domain.usecase.PayInvoicePaymentUseCaseImpl
import com.neoutils.finsight.domain.usecase.PayInvoiceUseCase
import com.neoutils.finsight.domain.usecase.PayInvoiceUseCaseImpl
import com.neoutils.finsight.domain.usecase.RecordingTransactionWriter
import com.neoutils.finsight.domain.usecase.StoppedClock
import com.neoutils.finsight.domain.usecase.SuggestCrossCurrencyAmountUseCase
import com.neoutils.finsight.domain.usecase.UpdateAdvanceInvoicePaymentUseCase
import com.neoutils.finsight.domain.usecase.ValidateAdvanceInvoicePaymentUseCase
import com.neoutils.finsight.domain.usecase.ValidateInvoicePaymentUseCase
import com.neoutils.finsight.domain.usecase.WriteInvoicePaymentUseCase
import com.neoutils.finsight.domain.usecase.testInvoice
import com.neoutils.finsight.testing.NoExchangeRates
import com.neoutils.finsight.ui.component.ModalManager
import com.neoutils.finsight.ui.screen.invoiceTransactions.FakeEntryRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime

/**
 * What a correction opens on, and **when** it opens on it.
 *
 * The three facades a payment names do not arrive together. The paying account is on the
 * leg already, hydrated; the card and the invoice are named there by identity only, and
 * turning identity into a facade takes repositories that answer later. The sheet is read
 * in between, so what it says meanwhile is not a transient nobody sees: a field
 * denominated in a currency about to be replaced withdraws what it shows the moment the
 * currency changes, which on a cross-currency correction is precisely the figure the
 * correction opened on.
 *
 * So the repositories here answer only when the test lets them, and the assertions are
 * made while they have not. A registration has no operation to open on and keeps taking
 * the default account, which is the other half of the same rule.
 */
class InvoicePaymentOpeningTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setup() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private val today = LocalDate(2026, 3, 20)

    private val card = CreditCard(
        id = 1,
        name = "Itaú",
        limit = 5_000.0,
        closingDay = 5,
        dueDay = 15,
        accountId = 10,
    )

    /** The card owes in reais; the account that pays it is denominated in dollars. */
    private val cardAccount = Account(id = 10, name = "Itaú", type = AccountType.LIABILITY, currency = "BRL")
    private val nubank = Account(id = 1, name = "Nubank", type = AccountType.ASSET, currency = "BRL", isDefault = true)
    private val wise = Account(id = 2, name = "Wise", type = AccountType.ASSET, currency = "USD")

    private val invoice = testInvoice(openingMonth = YearMonth(2026, 2), card = card)
    private val dimensionId = checkNotNull(invoice.dimensionId)

    /** R$ 500 settled on the invoice, US$ 90 out of Wise — the two monetary legs. */
    private val payment = Transaction(
        id = 7,
        title = null,
        date = LocalDate(2026, 3, 10),
        entries = listOf(
            Entry(id = 1, transactionId = 7, account = cardAccount, amount = 50_000, dimensionId = dimensionId),
            Entry(id = 2, transactionId = 7, account = wise, amount = -9_000),
        ),
    )

    /** Answers only once the test says so — a repository read is not instantaneous. */
    private val lookups = CompletableDeferred<Unit>()

    private inner class GatedCardRepository : ICreditCardRepository {
        override fun observeAllCreditCards(): Flow<List<CreditCard>> = MutableStateFlow(listOf(card))
        override suspend fun getAllCreditCards(): List<CreditCard> {
            lookups.await()
            return listOf(card)
        }
        override fun observeCreditCardById(creditCardId: Long): Flow<CreditCard?> = notAsked()
        override fun observeAllCreditCardsIncludingClosed(): Flow<List<CreditCard>> = notAsked()
        override suspend fun getAllCreditCardsIncludingClosed(): List<CreditCard> = notAsked()
        override suspend fun getCreditCardById(creditCardId: Long): CreditCard? = notAsked()
        override suspend fun insert(creditCard: CreditCard, currency: String): Long = notAsked()
        override suspend fun update(creditCard: CreditCard) = notAsked()
        override suspend fun delete(creditCard: CreditCard) = notAsked()
        override suspend fun unarchive(accountId: Long) = notAsked()
        override suspend fun currencyForNewCard(): String = notAsked()
    }

    private inner class GatedInvoiceRepository : IInvoiceRepository {
        override fun observeInvoicesByCreditCard(creditCardId: Long): Flow<List<Invoice>> =
            MutableStateFlow(listOf(invoice))
        override suspend fun getInvoicesByCreditCard(creditCardId: Long): List<Invoice> {
            lookups.await()
            return listOf(invoice)
        }
        override suspend fun getInvoiceById(id: Long): Invoice? = invoice.takeIf { it.id == id }
        override fun observeAllInvoices(): Flow<List<Invoice>> = notAsked()
        override fun observeInvoiceById(invoiceId: Long): Flow<Invoice?> = notAsked()
        override fun observeOpenInvoice(creditCardId: Long): Flow<Invoice?> = notAsked()
        override fun observeAvailableInvoices(creditCardId: Long): Flow<List<Invoice>> = notAsked()
        override fun observeUnpaidInvoice(creditCardId: Long): Flow<Invoice?> = notAsked()
        override fun observeUnpaidInvoices(): Flow<List<Invoice>> = notAsked()
        override fun observeInvoicesToSettle(month: YearMonth): Flow<List<Invoice>> = notAsked()
        override suspend fun getAllInvoices(): List<Invoice> = notAsked()
        override suspend fun getUnpaidInvoicesByCreditCard(creditCardId: Long): List<Invoice> = notAsked()
        override suspend fun getUnpaidInvoicesByCreditCards(creditCardIds: Collection<Long>): Map<Long, List<Invoice>> = throw NotImplementedError()
        override suspend fun getOpenInvoice(creditCardId: Long): Invoice? = notAsked()
        override suspend fun insert(invoice: Invoice): Invoice = notAsked()
        override suspend fun update(invoice: Invoice) = notAsked()
        override suspend fun deleteById(id: Long) = notAsked()
    }

    private inner class GatedAccountRepository : IAccountRepository {
        private val chart = listOf(nubank, wise, cardAccount)
        override fun observeAllAccounts(): Flow<List<Account>> = MutableStateFlow(listOf(nubank, wise))
        override suspend fun getAccountById(accountId: Long): Account? {
            lookups.await()
            return chart.firstOrNull { it.id == accountId }
        }
        override suspend fun getDefaultAccount(): Account = nubank
        override suspend fun hasYieldingAccount(): Boolean = false
        override fun observeAccountById(accountId: Long): Flow<Account?> = notAsked()
        override suspend fun getAllAccounts(): List<Account> = notAsked()
        override suspend fun getAllAccountsIncludingClosed(): List<Account> = notAsked()
        override fun observeAllAccountsIncludingClosed(): Flow<List<Account>> = notAsked()
        override suspend fun getAllLedgerAccounts(): List<Account> = notAsked()
        override fun observeAllLedgerAccounts(): Flow<List<Account>> = notAsked()
        override fun observeDefaultAccount(): Flow<Account?> = notAsked()
        override suspend fun getAccountCount(): Int = notAsked()
        override suspend fun insert(account: Account): Long = notAsked()
        override suspend fun update(account: Account) = notAsked()
        override suspend fun delete(account: Account) = notAsked()
        override suspend fun reopen(accountId: Long) = notAsked()
    }

    private object SilentAnalytics : Analytics {
        override fun logScreenView(screenName: String) = Unit
        override fun logEvent(event: Event) = Unit
        override fun setUserId(id: String?) = Unit
    }

    private object SilentCrashlytics : Crashlytics {
        override fun setUserId(id: String?) = Unit
        override fun recordException(e: Throwable) = Unit
    }

    private fun viewModel(transaction: Transaction?): InvoicePaymentViewModel {
        val accounts = GatedAccountRepository()
        val invoices = GatedInvoiceRepository()
        val clock = StoppedClock(today)
        val entries = FakeEntryRepository(
            owedByInvoiceId = mapOf(dimensionId to 800.0),
            entriesByTransactionId = mapOf(payment.id to payment.entries),
        )
        val calculate = CalculateInvoiceUseCaseImpl(entries)
        val write = WriteInvoicePaymentUseCase(
            transactionRepository = RecordingTransactionWriter(),
            harvestExchangeRate = HarvestExchangeRateUseCase(NoExchangeRates),
            accountRepository = accounts,
        )
        val validate = ValidateAdvanceInvoicePaymentUseCase(invoices, calculate, clock)

        return InvoicePaymentViewModel(
            initialInvoiceId = null,
            transaction = transaction,
            payInvoicePaymentUseCase = PayInvoicePaymentUseCaseImpl(
                clock = clock,
                validateInvoicePayment = ValidateInvoicePaymentUseCase(),
                writeInvoicePayment = write,
                invoiceRepository = invoices,
                calculateInvoiceUseCase = calculate,
                payInvoiceUseCase = PayInvoiceUseCaseImpl(invoices, ValidateInvoicePaymentUseCase(), clock),
                accountRepository = accounts,
            ),
            advanceInvoicePaymentUseCase = AdvanceInvoicePaymentUseCaseImpl(write, validate, accounts),
            updateAdvanceInvoicePaymentUseCase = UpdateAdvanceInvoicePaymentUseCase(
                writeInvoicePayment = write,
                validateInvoicePayment = validate,
                transactionRepository = RecordingTransactionWriter(),
            ),
            calculateInvoiceUseCase = calculate,
            suggestCrossCurrencyAmount = SuggestCrossCurrencyAmountUseCase(NoExchangeRates),
            creditCardRepository = GatedCardRepository(),
            invoiceRepository = invoices,
            accountRepository = accounts,
            modalManager = ModalManager(),
            analytics = SilentAnalytics,
            crashlytics = SilentCrashlytics,
            clock = clock,
        )
    }

    @Test
    fun `the payer is named before any lookup answers`() = runTest(dispatcher) {
        viewModel(payment).uiState.test {
            val opening = awaitContent()

            assertEquals(
                wise.id,
                opening.selectedAccount?.id,
                "the account the operation records, not the default one standing in for it",
            )
            assertEquals(
                "USD",
                opening.selectedAccount?.currency,
                "the currency the amount that leaves the account is denominated in",
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the lookups refine the opening without displacing the payer`() = runTest(dispatcher) {
        viewModel(payment).uiState.test {
            awaitContent()
            lookups.complete(Unit)

            val settled = awaitContentWhere { it.selectedInvoice != null }

            assertEquals(card.id, settled.selectedCreditCard?.id)
            assertEquals(invoice.id, settled.selectedInvoice?.id)
            assertEquals(wise.id, settled.selectedAccount?.id, "still the payer it opened on")
            assertEquals("BRL", settled.invoiceCurrency)
            assertTrue(settled.isCrossCurrency, "the two ends are denominated differently")
            assertTrue(
                settled.showsRecordedOperation,
                "opening is not a stated intention: nothing has been switched",
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a registration has no payer to open on and takes the default account`() =
        runTest(dispatcher) {
            lookups.complete(Unit)

            viewModel(transaction = null).uiState.test {
                val opening = awaitContent()

                assertEquals(nubank.id, opening.selectedAccount?.id)
                assertFalse(opening.isEditMode)
                assertFalse(
                    opening.showsRecordedOperation,
                    "a registration is never showing a record",
                )

                cancelAndIgnoreRemainingEvents()
            }
        }

    private suspend fun app.cash.turbine.TurbineTestContext<InvoicePaymentUiState>.awaitContent() =
        awaitContentWhere { true }

    private suspend fun app.cash.turbine.TurbineTestContext<InvoicePaymentUiState>.awaitContentWhere(
        predicate: (InvoicePaymentUiState.Content) -> Boolean,
    ): InvoicePaymentUiState.Content {
        while (true) {
            val state = awaitItem()
            if (state is InvoicePaymentUiState.Content && predicate(state)) return state
        }
    }
}

private fun notAsked(): Nothing = error("not part of what opening the payment sheet reads")
