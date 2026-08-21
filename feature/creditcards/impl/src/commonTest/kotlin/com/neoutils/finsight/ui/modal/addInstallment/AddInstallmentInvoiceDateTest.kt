@file:OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)

package com.neoutils.finsight.ui.modal.addInstallment

import app.cash.turbine.test
import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.error.BuildTransactionError
import com.neoutils.finsight.util.dayMonthYear
import com.neoutils.finsight.domain.analytics.Event
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.form.TransactionForm
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.repository.IInvoiceRepository
import com.neoutils.finsight.domain.usecase.AddInstallmentUseCase
import com.neoutils.finsight.domain.usecase.ValidateTransactionFormUseCase
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
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * The instalment sheet obeys the same hierarchy as the transaction one: the invoice governs
 * the date, the date governs nothing. It matters more here — the instalments are laid out
 * one month apart from this date, so a first one outside its own invoice's window drags the
 * whole arrangement with it.
 *
 * The card closes on the 10th and falls due on the 20th, so the invoice due in April admits
 * purchases from 10 March to 10 April, and today — 12 March — is inside it.
 */
class AddInstallmentInvoiceDateTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setup() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private val today = LocalDate(2026, 3, 12)

    private val card = CreditCard(
        id = 1,
        name = "Card",
        limit = 5000.0,
        closingDay = 10,
        dueDay = 20,
        accountId = 100,
    )

    private val cardAccount = Account(
        id = 100,
        name = "Card",
        type = AccountType.LIABILITY,
        currency = "BRL",
    )

    private val openInvoice = Invoice(
        id = 1,
        creditCard = card,
        openingMonth = YearMonth(2026, 3),
        closingMonth = YearMonth(2026, 4),
        dueMonth = YearMonth(2026, 4),
        status = Invoice.Status.OPEN,
    )

    @Test
    fun `the sheet opens on today, untouched by the invoice it selected`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.uiState.test {
            advanceUntilIdle()

            val state = expectMostRecentItem()
            assertEquals("12/03/2026", state.form.date)
            assertEquals(YearMonth(2026, 4), state.invoiceSelection?.dueMonth)
        }
    }

    @Test
    fun `navigating to an earlier invoice moves the date into its window`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.uiState.test {
            advanceUntilIdle()

            viewModel.onAction(AddInstallmentAction.NavigateToMonth(YearMonth(2026, 3)))
            advanceUntilIdle()

            // 10/02–10/03: the 12th is past the closing day, so it belongs to February.
            assertEquals("12/02/2026", expectMostRecentItem().form.date)
        }
    }

    @Test
    fun `a future invoice holds the date at today`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.uiState.test {
            advanceUntilIdle()

            viewModel.onAction(AddInstallmentAction.NavigateToMonth(YearMonth(2026, 8)))
            advanceUntilIdle()

            assertEquals("12/03/2026", expectMostRecentItem().form.date)
        }
    }

    @Test
    fun `editing the date leaves the invoice where it is`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.uiState.test {
            advanceUntilIdle()

            viewModel.onAction(AddInstallmentAction.ChangeDate("05/01/2026"))
            advanceUntilIdle()

            val state = expectMostRecentItem()
            assertEquals("05/01/2026", state.form.date)
            assertEquals(YearMonth(2026, 4), state.invoiceSelection?.dueMonth)
        }
    }

    private fun viewModel() = AddInstallmentViewModel(
        categoryRepository = MuteCategories,
        accountRepository = CardAccounts(cardAccount),
        creditCardRepository = OneCard(card),
        invoiceRepository = OneInvoice(openInvoice),
        addInstallmentUseCase = Unwritten,
        validateTransactionForm = ParseTheDate,
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

/**
 * The real rule lives in `:feature:transactions:impl`, which this module cannot see, and is
 * exercised where it lives. What these tests need from it is only the date it reads back.
 */
private object ParseTheDate : ValidateTransactionFormUseCase {
    override fun invoke(form: TransactionForm): Either<BuildTransactionError, LocalDate> =
        runCatching { dayMonthYear.parse(form.date) }
            .fold({ it.right() }, { BuildTransactionError.DateInvalid.left() })
}

/** Nothing here writes: these tests stop at what the form holds. */
private object Unwritten : AddInstallmentUseCase {
    override suspend operator fun invoke(
        form: TransactionForm,
        installments: Int,
    ): Nothing = throw NotImplementedError()
}

private object MuteCategories : ICategoryRepository {
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

private class OneInvoice(private val invoice: Invoice) : IInvoiceRepository {
    override suspend fun getInvoicesByCreditCard(creditCardId: Long) =
        listOf(invoice).filter { it.creditCard.id == creditCardId }

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
    override suspend fun getOpenInvoice(creditCardId: Long): Invoice? = throw NotImplementedError()
    override suspend fun getInvoiceById(id: Long): Invoice? = throw NotImplementedError()
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
