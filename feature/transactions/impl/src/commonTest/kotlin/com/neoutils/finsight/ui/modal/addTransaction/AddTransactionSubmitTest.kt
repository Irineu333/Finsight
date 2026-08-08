@file:OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)

package com.neoutils.finsight.ui.modal.addTransaction

import app.cash.turbine.test
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.analytics.Event
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.TransactionTarget
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionIntent
import com.neoutils.finsight.domain.model.form.TransactionForm
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.repository.IInvoiceRepository
import com.neoutils.finsight.domain.repository.ITransactionRepository
import com.neoutils.finsight.domain.usecase.AddInstallmentUseCase
import com.neoutils.finsight.domain.usecase.BuildTransactionUseCase
import com.neoutils.finsight.domain.usecase.ValidateTransactionFormUseCaseImpl
import com.neoutils.finsight.ui.component.ModalManager
import com.neoutils.finsight.ui.modal.FakeCrashlytics
import com.neoutils.finsight.ui.modal.FakeTransactionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
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
 * Whether the sheet offers its submit — and, above all, *against which today* it decides.
 *
 * The rule itself lives on [TransactionForm] and is exercised there. What is new here is
 * that the decision is reachable at all: it used to be taken in the composable, where a
 * test can only get at it through a device. It moved to the ViewModel, which is the layer
 * that holds a `Clock`, so the clock can simply be handed over — as a build that moves
 * time does, and as these tests do.
 */
class AddTransactionSubmitTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setup() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private val account = Account(id = 1, name = "Wallet", type = AccountType.ASSET, currency = "BRL", isDefault = true)

    @Test
    fun `an untouched form offers nothing to submit`() = runTest(dispatcher) {
        val viewModel = viewModel(today = LocalDate(2026, 3, 10))

        viewModel.uiState.test {
            advanceUntilIdle()
            val state = expectMostRecentItem()

            assertEquals("10/03/2026", state.form.date, "the date field opens on the injected clock")
            assertFalse(state.canSubmit, "no amount, no title and no category")
        }
    }

    @Test
    fun `a filled form is submittable and dated by the clock the ViewModel was given`() = runTest(dispatcher) {
        val viewModel = viewModel(today = LocalDate(2026, 3, 10))

        viewModel.uiState.test {
            viewModel.onAction(AddTransactionAction.ChangeTitle("Lunch"))
            viewModel.onAction(AddTransactionAction.ChangeAmount("4500"))
            advanceUntilIdle()

            assertTrue(expectMostRecentItem().canSubmit)
        }
    }

    /**
     * The regression this whole thing is about. `10/04/2026` is the future for a clock on
     * 10 March and the present for one moved a month on — and *only* the ViewModel's clock
     * decides which. Before, the composable answered with a clock of its own, and the two
     * could disagree with nothing on screen to say so.
     */
    @Test
    fun `a date the injected clock has not reached yet is refused and a reached one is accepted`() = runTest(dispatcher) {
        val beforeJump = viewModel(today = LocalDate(2026, 3, 10))

        beforeJump.uiState.test {
            beforeJump.fill(date = "10/04/2026")
            advanceUntilIdle()

            assertFalse(expectMostRecentItem().canSubmit, "10/04 is still the future on 10/03")
        }

        val afterJump = viewModel(today = LocalDate(2026, 4, 10))

        afterJump.uiState.test {
            afterJump.fill(date = "10/04/2026")
            advanceUntilIdle()

            assertTrue(expectMostRecentItem().canSubmit, "the same date, once the clock is on it")
        }
    }

    /**
     * Switching to income and back keeps the card target, which is why the picked target
     * is carried beside the form rather than read off it — the form normalises an income
     * onto an account, and reading it back would forget the choice.
     */
    @Test
    fun `the picked target survives a round trip through income`() = runTest(dispatcher) {
        val viewModel = viewModel(today = LocalDate(2026, 3, 10))

        viewModel.uiState.test {
            viewModel.onAction(AddTransactionAction.ChangeTarget(TransactionTarget.CREDIT_CARD))
            viewModel.onAction(AddTransactionAction.ChangeType(TransactionType.INCOME))
            advanceUntilIdle()

            val income = expectMostRecentItem()
            assertEquals(TransactionTarget.ACCOUNT, income.form.target, "an income lands on an account")
            assertEquals(TransactionTarget.CREDIT_CARD, income.selectedTarget, "what they picked is remembered")

            viewModel.onAction(AddTransactionAction.ChangeType(TransactionType.EXPENSE))
            advanceUntilIdle()

            assertEquals(TransactionTarget.CREDIT_CARD, expectMostRecentItem().form.target)
        }
    }

    private fun AddTransactionViewModel.fill(date: String) {
        onAction(AddTransactionAction.ChangeTitle("Lunch"))
        onAction(AddTransactionAction.ChangeAmount("4500"))
        onAction(AddTransactionAction.ChangeDate(date))
    }

    private fun viewModel(today: LocalDate) = AddTransactionViewModel(
        categoryRepository = FakeCategoryRepository,
        creditCardRepository = FakeCreditCardRepository,
        invoiceRepository = FakeInvoiceRepository,
        transactionRepository = FakeTransactionRepository(),
        accountRepository = FakeAccountRepository(account),
        buildTransactionUseCase = NotWritten,
        addInstallmentUseCase = NotWritten,
        modalManager = ModalManager(),
        analytics = FakeAnalytics,
        crashlytics = FakeCrashlytics(),
        validateTransactionForm = ValidateTransactionFormUseCaseImpl(clock = FixedClock(today)),
        clock = FixedClock(today),
    )
}

private class FixedClock(private val today: LocalDate) : Clock {
    override fun now(): Instant = today.atStartOfDayIn(TimeZone.currentSystemDefault())
}

private object FakeAnalytics : Analytics {
    override fun logScreenView(screenName: String) = Unit
    override fun logEvent(event: Event) = Unit
    override fun setUserId(id: String?) = Unit
}

/** Nothing here writes: these tests stop at the submit being offered. */
private object NotWritten : BuildTransactionUseCase, AddInstallmentUseCase {
    override suspend operator fun invoke(form: TransactionForm) = throw NotImplementedError()
    override suspend operator fun invoke(form: TransactionForm, installments: Int) = throw NotImplementedError()
}

private object FakeCategoryRepository : ICategoryRepository {
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

private object FakeCreditCardRepository : ICreditCardRepository {
    override fun observeAllCreditCards(): Flow<List<CreditCard>> = flowOf(emptyList())
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

private object FakeInvoiceRepository : IInvoiceRepository {
    override suspend fun getInvoicesByCreditCard(creditCardId: Long): List<Invoice> = emptyList()
    override fun observeAllInvoices(): Flow<List<Invoice>> = throw NotImplementedError()
    override fun observeInvoicesByCreditCard(creditCardId: Long): Flow<List<Invoice>> = throw NotImplementedError()
    override fun observeInvoiceById(invoiceId: Long): Flow<Invoice?> = throw NotImplementedError()
    override fun observeOpenInvoice(creditCardId: Long): Flow<Invoice?> = throw NotImplementedError()
    override fun observeAvailableInvoices(creditCardId: Long): Flow<List<Invoice>> = throw NotImplementedError()
    override fun observeUnpaidInvoice(creditCardId: Long): Flow<Invoice?> = throw NotImplementedError()
    override fun observeUnpaidInvoices(): Flow<List<Invoice>> = throw NotImplementedError()
    override suspend fun getAllInvoices(): List<Invoice> = throw NotImplementedError()
    override suspend fun getUnpaidInvoicesByCreditCard(creditCardId: Long): List<Invoice> = throw NotImplementedError()
    override suspend fun getOpenInvoice(creditCardId: Long): Invoice? = throw NotImplementedError()
    override suspend fun getInvoiceById(id: Long): Invoice? = throw NotImplementedError()
    override suspend fun insert(invoice: Invoice): Invoice = throw NotImplementedError()
    override suspend fun update(invoice: Invoice) = throw NotImplementedError()
    override suspend fun deleteById(id: Long) = throw NotImplementedError()
}

private class FakeAccountRepository(private val account: Account) : IAccountRepository {
    override fun observeAllAccounts(): Flow<List<Account>> = flowOf(listOf(account))
    override fun observeAllAccountsIncludingClosed(): Flow<List<Account>> = throw NotImplementedError()
    override fun observeAllLedgerAccounts(): Flow<List<Account>> = throw NotImplementedError()
    override fun observeAccountById(accountId: Long): Flow<Account?> = throw NotImplementedError()
    override fun observeDefaultAccount(): Flow<Account?> = throw NotImplementedError()
    override suspend fun getAllAccounts(): List<Account> = throw NotImplementedError()
    override suspend fun getAllAccountsIncludingClosed(): List<Account> = throw NotImplementedError()
    override suspend fun getAllLedgerAccounts(): List<Account> = throw NotImplementedError()
    override suspend fun getAccountById(accountId: Long): Account? = throw NotImplementedError()
    override suspend fun getDefaultAccount(): Account? = throw NotImplementedError()
    override suspend fun getAccountCount(): Int = throw NotImplementedError()
    override suspend fun hasYieldingAccount(): Boolean = throw NotImplementedError()
    override suspend fun insert(account: Account): Long = throw NotImplementedError()
    override suspend fun update(account: Account) = throw NotImplementedError()
    override suspend fun delete(account: Account) = throw NotImplementedError()
    override suspend fun reopen(accountId: Long) = throw NotImplementedError()
}
