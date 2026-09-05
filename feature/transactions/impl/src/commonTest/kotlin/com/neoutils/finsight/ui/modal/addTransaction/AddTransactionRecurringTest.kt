@file:OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)

package com.neoutils.finsight.ui.modal.addTransaction

import app.cash.turbine.test
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.analytics.Event
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.ContraLeg
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.TransactionIntent
import com.neoutils.finsight.domain.model.TransactionLeg
import com.neoutils.finsight.domain.model.TransactionTarget
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.domain.model.form.TransactionForm
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.repository.IInvoiceRepository
import com.neoutils.finsight.domain.usecase.AddInstallmentUseCase
import com.neoutils.finsight.domain.usecase.BuildTransactionUseCase
import com.neoutils.finsight.domain.usecase.RegisterTransactionUseCaseImpl
import com.neoutils.finsight.domain.usecase.StartRecurringFromTransactionUseCase
import com.neoutils.finsight.domain.usecase.ValidateTransactionFormUseCaseImpl
import com.neoutils.finsight.ui.component.ErrorModal
import com.neoutils.finsight.ui.component.ModalManager
import com.neoutils.finsight.ui.modal.FakeCrashlytics
import com.neoutils.finsight.ui.modal.FakeTransactionRepository
import com.neoutils.finsight.ui.modal.RecordingRecurringRepository
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
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.yearMonth
import kotlinx.datetime.YearMonth
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Marking a transaction as recurring is form state until it is saved, and at the save it
 * is the recurring feature — not this screen — that decides how a template is born.
 *
 * What these pin down is the whole of the screen's part: the mark writes nothing on its
 * own, it cannot coexist with instalments, and the intent handed over is the one the
 * screen already built, with the invoice the user picked still on it.
 */
class AddTransactionRecurringTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setup() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private val today = LocalDate(2026, 3, 12)

    private val account = Account(
        id = 1,
        name = "Wallet",
        type = AccountType.ASSET,
        currency = "BRL",
        isDefault = true,
    )

    /** The dimension of the invoice the user picked — it must survive to the template. */
    private val invoiceDimension = 77L

    @Test
    fun `marking writes nothing and changes nothing about the submit`() = runTest(dispatcher) {
        val recurringRepository = RecordingRecurringRepository()
        val transactions = FakeTransactionRepository()
        val viewModel = viewModel(recurringRepository, transactions)

        viewModel.uiState.test {
            viewModel.fill()
            advanceUntilIdle()
            val before = expectMostRecentItem()

            viewModel.onAction(AddTransactionAction.ChangeRecurring(true))
            advanceUntilIdle()
            val after = expectMostRecentItem()

            assertTrue(after.isRecurring)
            assertEquals(before.canSubmit, after.canSubmit)
            assertTrue(recurringRepository.created.isEmpty(), "nothing is written before the save")
        }
    }

    @Test
    fun `marking and unmarking saves a transaction like any other`() = runTest(dispatcher) {
        val recurringRepository = RecordingRecurringRepository()
        val transactions = FakeTransactionRepository()
        val viewModel = viewModel(recurringRepository, transactions)

        viewModel.uiState.test {
            viewModel.fill()
            advanceUntilIdle()
            viewModel.onAction(AddTransactionAction.ChangeRecurring(true))
            viewModel.onAction(AddTransactionAction.ChangeRecurring(false))
            viewModel.onAction(AddTransactionAction.Submit)
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(1, transactions.created.size)
        assertTrue(recurringRepository.created.isEmpty())
    }

    @Test
    fun `splitting into instalments drops the mark, and going back offers it unmarked`() =
        runTest(dispatcher) {
            val viewModel = viewModel()

            viewModel.uiState.test {
                viewModel.fill()
                // Instalments only exist on a card — the form normalises them away
                // anywhere else, so this is where the exclusion can be observed.
                viewModel.onAction(AddTransactionAction.ChangeTarget(TransactionTarget.CREDIT_CARD))
                viewModel.onAction(AddTransactionAction.ChangeRecurring(true))
                viewModel.onAction(AddTransactionAction.ChangeInstallments(3))
                advanceUntilIdle()

                val split = expectMostRecentItem()
                assertFalse(split.isRecurring, "the mark is dropped, not merely hidden")
                assertFalse(split.canRepeat)

                viewModel.onAction(AddTransactionAction.ChangeInstallments(1))
                advanceUntilIdle()

                val whole = expectMostRecentItem()
                assertTrue(whole.canRepeat)
                assertFalse(whole.isRecurring, "it comes back unmarked")
            }
        }

    @Test
    fun `saving marked hands over the intent the screen built, invoice and all`() =
        runTest(dispatcher) {
            val recurringRepository = RecordingRecurringRepository()
            val transactions = FakeTransactionRepository()
            val viewModel = viewModel(recurringRepository, transactions)

            viewModel.uiState.test {
                viewModel.fill()
                advanceUntilIdle()
                viewModel.onAction(AddTransactionAction.ChangeRecurring(true))
                viewModel.onAction(AddTransactionAction.Submit)
                advanceUntilIdle()
                cancelAndIgnoreRemainingEvents()
            }

            val created = recurringRepository.created.single()
            assertEquals(
                invoiceDimension,
                created.firstCycle.legs.single().dimensionId,
                "the intent is completed, never rebuilt",
            )
            assertEquals(1, created.firstCycle.recurringCycle)
            assertEquals(today.day, created.recurring.dayOfMonth)
            assertEquals(today.yearMonth, created.occurrence.yearMonth)
            assertTrue(
                transactions.created.isEmpty(),
                "the transaction is written by the cycle, not beside it",
            )
        }

    @Test
    fun `a refused save is explained and leaves the sheet open`() = runTest(dispatcher) {
        val modalManager = ModalManager()
        val crashlytics = FakeCrashlytics()
        val viewModel = viewModel(
            recurringRepository = RecordingRecurringRepository(
                failure = IllegalStateException("closed invoice"),
            ),
            modalManager = modalManager,
            crashlytics = crashlytics,
        )

        viewModel.uiState.test {
            viewModel.fill()
            advanceUntilIdle()
            viewModel.onAction(AddTransactionAction.ChangeRecurring(true))
            viewModel.onAction(AddTransactionAction.Submit)
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }

        assertIs<ErrorModal>(modalManager.top, "the refusal is said, not swallowed")
        assertEquals(1, crashlytics.recorded.size)
    }

    private fun AddTransactionViewModel.fill() {
        onAction(AddTransactionAction.ChangeTitle("Rent"))
        onAction(AddTransactionAction.ChangeAmount("240000"))
        onAction(AddTransactionAction.ChangeDate("12/03/2026"))
    }

    private fun viewModel(
        recurringRepository: RecordingRecurringRepository = RecordingRecurringRepository(),
        transactionRepository: FakeTransactionRepository = FakeTransactionRepository(),
        modalManager: ModalManager = ModalManager(),
        crashlytics: FakeCrashlytics = FakeCrashlytics(),
    ) = AddTransactionViewModel(
        // Opened from nowhere in particular: nothing is preselected.
        origin = null,
        categoryRepository = FakeCategories,
        creditCardRepository = FakeCards,
        invoiceRepository = FakeInvoices,
        accountRepository = FakeAccounts(account),
        // The real register, so the screen's part is exercised against the dispatch it
        // delegates to rather than against a stand-in for it.
        registerTransaction = RegisterTransactionUseCaseImpl(
            transactionRepository = transactionRepository,
            buildTransaction = BuildsWithInvoice(invoiceDimension),
            addInstallment = NotWritten,
            startRecurringFromTransaction = StartRecurringFromTransactionUseCase(
                repository = recurringRepository,
                clock = ClockOn(today),
            ),
        ),
        modalManager = modalManager,
        analytics = NoAnalytics,
        crashlytics = crashlytics,
        validateTransactionForm = ValidateTransactionFormUseCaseImpl(clock = ClockOn(today)),
        clock = ClockOn(today),
    )

    /** Stands in for the real build: what matters here is that its output travels intact. */

    private class BuildsWithInvoice(private val dimensionId: Long) : BuildTransactionUseCase {
        override suspend fun invoke(form: TransactionForm) = arrow.core.Either.Right(
            TransactionIntent(
                title = form.title,
                date = LocalDate(2026, 3, 12),
                legs = listOf(
                    TransactionLeg(
                        type = form.type,
                        amount = 2400.0,
                        accountId = 1L,
                        dimensionId = dimensionId,
                    )
                ),
                contra = null,
            )
        )
    }

    private object NotWritten : AddInstallmentUseCase {
        override suspend fun invoke(form: TransactionForm, installments: Int) = throw NotImplementedError()
    }

    private class ClockOn(private val today: LocalDate) : Clock {
        override fun now(): Instant = today.atStartOfDayIn(TimeZone.currentSystemDefault())
    }

    private object NoAnalytics : Analytics {
        override fun logScreenView(screenName: String) = Unit
        override fun logEvent(event: Event) = Unit
        override fun setUserId(id: String?) = Unit
    }

    private object FakeCategories : ICategoryRepository {
        override fun observeAllCategories(): Flow<List<Category>> = flowOf(emptyList())
        override fun observeAllCategoriesIncludingClosed(): Flow<List<Category>> = throw NotImplementedError()
        override fun observeCategoriesByType(type: Category.Type): Flow<List<Category>> = throw NotImplementedError()
        override fun observeCategoryById(id: Long): Flow<Category?> = throw NotImplementedError()
        override suspend fun getAllCategories(): List<Category> = throw NotImplementedError()
        override suspend fun getAllCategoriesIncludingClosed(): List<Category> = throw NotImplementedError()
        override suspend fun getCategoryById(id: Long): Category? = throw NotImplementedError()
        override suspend fun getCategoryByDimensionId(dimensionId: Long): Category? = throw NotImplementedError()
        override suspend fun getCategoryBySystemKey(systemKey: String): Category? = throw NotImplementedError()
        override suspend fun archive(id: Long) = throw NotImplementedError()
        override suspend fun unarchive(id: Long) = throw NotImplementedError()
        override suspend fun existsByName(name: String, ignoreId: Long): Boolean = throw NotImplementedError()
        override suspend fun insert(category: Category) = throw NotImplementedError()
        override suspend fun insertAll(categories: List<Category>) = throw NotImplementedError()
        override suspend fun update(category: Category) = throw NotImplementedError()
        override suspend fun delete(category: Category) = throw NotImplementedError()
    }

    private object FakeCards : ICreditCardRepository {
        val card = CreditCard(
            id = 1,
            accountId = 2,
            name = "Card",
            limit = 5000.0,
            closingDay = 20,
            dueDay = 27,
        )

        override fun observeAllCreditCards(): Flow<List<CreditCard>> = flowOf(listOf(card))
        override fun observeAllCreditCardsIncludingClosed(): Flow<List<CreditCard>> = throw NotImplementedError()
        override fun observeCreditCardById(creditCardId: Long): Flow<CreditCard?> = throw NotImplementedError()
        override suspend fun getAllCreditCards(): List<CreditCard> = throw NotImplementedError()
        override suspend fun getAllCreditCardsIncludingClosed(): List<CreditCard> = throw NotImplementedError()
        override suspend fun getCreditCardById(creditCardId: Long): CreditCard? = throw NotImplementedError()
        override suspend fun insert(creditCard: CreditCard, currency: String): Long = throw NotImplementedError()
        override suspend fun currencyForNewCard(): String = throw NotImplementedError()
        override suspend fun update(creditCard: CreditCard) = throw NotImplementedError()
        override suspend fun delete(creditCard: CreditCard) = throw NotImplementedError()
        override suspend fun unarchive(accountId: Long) = throw NotImplementedError()
    }

    private object FakeInvoices : IInvoiceRepository {
        override suspend fun getInvoicesByCreditCard(creditCardId: Long): List<Invoice> = emptyList()
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
        override suspend fun getUnpaidInvoicesByCreditCards(creditCardIds: Collection<Long>): Map<Long, List<Invoice>> = throw NotImplementedError()
        override suspend fun getOpenInvoice(creditCardId: Long): Invoice? = throw NotImplementedError()
        override suspend fun getInvoiceById(id: Long): Invoice? = throw NotImplementedError()
        override suspend fun insert(invoice: Invoice): Invoice = throw NotImplementedError()
        override suspend fun update(invoice: Invoice) = throw NotImplementedError()
        override suspend fun deleteById(id: Long) = throw NotImplementedError()
    }

    private class FakeAccounts(private val account: Account) : IAccountRepository {
        override fun observeAllAccounts(): Flow<List<Account>> = flowOf(listOf(account))
        override fun observeAllAccountsIncludingClosed(): Flow<List<Account>> = throw NotImplementedError()
        override fun observeAllLedgerAccounts(): Flow<List<Account>> = throw NotImplementedError()
        override fun observeAccountById(accountId: Long): Flow<Account?> = throw NotImplementedError()
        override fun observeDefaultAccount(): Flow<Account?> = throw NotImplementedError()
        override suspend fun getAllAccounts(): List<Account> = throw NotImplementedError()
        override suspend fun getAllAccountsIncludingClosed(): List<Account> = throw NotImplementedError()
        override suspend fun getAllLedgerAccounts(): List<Account> = throw NotImplementedError()
        override suspend fun getAccountById(accountId: Long): Account? = null
        override suspend fun getDefaultAccount(): Account? = throw NotImplementedError()
        override suspend fun getAccountCount(): Int = throw NotImplementedError()
        override suspend fun hasYieldingAccount(): Boolean = throw NotImplementedError()
        override suspend fun insert(account: Account): Long = throw NotImplementedError()
        override suspend fun update(account: Account) = throw NotImplementedError()
        override suspend fun delete(account: Account) = throw NotImplementedError()
        override suspend fun reopen(accountId: Long) = throw NotImplementedError()
    }
}
