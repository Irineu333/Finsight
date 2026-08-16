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
import com.neoutils.finsight.domain.model.form.TransactionForm
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.repository.IInvoiceRepository
import com.neoutils.finsight.domain.usecase.RegisterTransactionUseCase
import com.neoutils.finsight.domain.usecase.ValidateTransactionFormUseCaseImpl
import com.neoutils.finsight.ui.component.ModalManager
import com.neoutils.finsight.ui.modal.FakeCrashlytics
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
 * The invoice governs the date, and nothing governs back.
 *
 * The card closes on the 10th and falls due on the 20th, so the invoice due in April admits
 * purchases from 10 March to 10 April — and today, 12 March, is inside it. That is why
 * opening the sheet leaves the date alone while navigating away from it does not.
 */
class AddTransactionInvoiceDateTest {

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

    private val card = CreditCard(
        id = 1,
        name = "Card",
        limit = 5000.0,
        closingDay = 10,
        dueDay = 20,
        accountId = 100,
    )

    /** Closes on the 5th, so the same due month names a window shifted five days earlier. */
    private val otherCard = card.copy(id = 2, name = "Other", closingDay = 5, dueDay = 15, accountId = 200)

    private fun openInvoiceOf(card: CreditCard) = Invoice(
        id = card.id,
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
            viewModel.onAction(AddTransactionAction.ChangeTarget(TransactionTarget.CREDIT_CARD))
            advanceUntilIdle()

            val state = expectMostRecentItem()
            assertEquals("12/03/2026", state.form.date, "today is inside the open invoice's window")
            assertEquals(YearMonth(2026, 4), state.invoiceSelection?.dueMonth)
        }
    }

    @Test
    fun `navigating to the previous invoice moves the date into its window`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.uiState.test {
            viewModel.onAction(AddTransactionAction.ChangeTarget(TransactionTarget.CREDIT_CARD))
            advanceUntilIdle()

            viewModel.onAction(AddTransactionAction.SelectInvoiceMonth(YearMonth(2026, 3)))
            advanceUntilIdle()

            // 10/02–10/03: the 12th is past the closing day, so it belongs to February.
            assertEquals("12/02/2026", expectMostRecentItem().form.date)
        }
    }

    @Test
    fun `navigating to a retroactive invoice reaches back a whole cycle`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.uiState.test {
            viewModel.onAction(AddTransactionAction.ChangeTarget(TransactionTarget.CREDIT_CARD))
            advanceUntilIdle()

            viewModel.onAction(AddTransactionAction.SelectInvoiceMonth(YearMonth(2026, 1)))
            advanceUntilIdle()

            assertEquals("12/12/2025", expectMostRecentItem().form.date)
        }
    }

    @Test
    fun `a future invoice holds the date at today`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.uiState.test {
            viewModel.onAction(AddTransactionAction.ChangeTarget(TransactionTarget.CREDIT_CARD))
            advanceUntilIdle()

            viewModel.onAction(AddTransactionAction.SelectInvoiceMonth(YearMonth(2026, 6)))
            advanceUntilIdle()

            assertEquals(
                "12/03/2026",
                expectMostRecentItem().form.date,
                "spending happens in the present and merely falls due later",
            )
        }
    }

    @Test
    fun `coming back from a future invoice lands on today again`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.uiState.test {
            viewModel.onAction(AddTransactionAction.ChangeTarget(TransactionTarget.CREDIT_CARD))
            viewModel.onAction(AddTransactionAction.SelectInvoiceMonth(YearMonth(2026, 6)))
            advanceUntilIdle()

            viewModel.onAction(AddTransactionAction.SelectInvoiceMonth(YearMonth(2026, 4)))
            advanceUntilIdle()

            assertEquals("12/03/2026", expectMostRecentItem().form.date)
        }
    }

    @Test
    fun `changing the card places the date in the new card's window`() = runTest(dispatcher) {
        val viewModel = viewModel(cards = listOf(card, otherCard))

        viewModel.uiState.test {
            viewModel.onAction(AddTransactionAction.ChangeTarget(TransactionTarget.CREDIT_CARD))
            viewModel.onAction(AddTransactionAction.SelectCreditCard(card))
            advanceUntilIdle()

            viewModel.onAction(AddTransactionAction.SelectInvoiceMonth(YearMonth(2026, 3)))
            advanceUntilIdle()

            assertEquals("12/02/2026", expectMostRecentItem().form.date)

            viewModel.onAction(AddTransactionAction.SelectCreditCard(otherCard))
            advanceUntilIdle()

            // The other card's open invoice is its own, and its window holds today.
            assertEquals("12/03/2026", expectMostRecentItem().form.date)
        }
    }

    @Test
    fun `editing the date leaves the invoice where it is`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.uiState.test {
            viewModel.onAction(AddTransactionAction.ChangeTarget(TransactionTarget.CREDIT_CARD))
            advanceUntilIdle()

            viewModel.onAction(AddTransactionAction.ChangeDate("05/01/2026"))
            advanceUntilIdle()

            val state = expectMostRecentItem()
            assertEquals("05/01/2026", state.form.date, "what they wrote stands")
            assertEquals(
                YearMonth(2026, 4),
                state.invoiceSelection?.dueMonth,
                "and it did not drag the invoice with it",
            )
            assertTrue(state.isDateOutsideInvoice, "the sheet says so, and stops there")
        }
    }

    @Test
    fun `a date the invoice admits is not flagged`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.uiState.test {
            viewModel.onAction(AddTransactionAction.ChangeTarget(TransactionTarget.CREDIT_CARD))
            advanceUntilIdle()

            assertFalse(expectMostRecentItem().isDateOutsideInvoice, "today opened inside it")

            viewModel.onAction(AddTransactionAction.SelectInvoiceMonth(YearMonth(2026, 1)))
            advanceUntilIdle()

            assertFalse(
                expectMostRecentItem().isDateOutsideInvoice,
                "and the projection put it inside the new one",
            )
        }
    }

    @Test
    fun `the day they wrote is the day the next projection keeps`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.uiState.test {
            viewModel.onAction(AddTransactionAction.ChangeTarget(TransactionTarget.CREDIT_CARD))
            advanceUntilIdle()

            viewModel.onAction(AddTransactionAction.ChangeDate("03/03/2026"))
            advanceUntilIdle()

            viewModel.onAction(AddTransactionAction.SelectInvoiceMonth(YearMonth(2026, 3)))
            advanceUntilIdle()

            // The 3rd precedes the closing day, so it stays in March — only the cycle changed.
            assertEquals("03/03/2026", expectMostRecentItem().form.date)
        }
    }

    @Test
    fun `a half-typed date does not block the projection`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.uiState.test {
            viewModel.onAction(AddTransactionAction.ChangeTarget(TransactionTarget.CREDIT_CARD))
            advanceUntilIdle()

            viewModel.onAction(AddTransactionAction.ChangeDate("05/0"))
            advanceUntilIdle()

            viewModel.onAction(AddTransactionAction.SelectInvoiceMonth(YearMonth(2026, 3)))
            advanceUntilIdle()

            assertEquals("12/02/2026", expectMostRecentItem().form.date, "today's day stands in")
        }
    }

    private fun viewModel(cards: List<CreditCard> = listOf(card)) = AddTransactionViewModel(
        categoryRepository = FakeCategories,
        creditCardRepository = FakeCards(cards),
        invoiceRepository = FakeInvoices(cards.map(::openInvoiceOf)),
        accountRepository = FakeAccounts(account),
        registerTransaction = Unwritten,
        modalManager = ModalManager(),
        analytics = MuteAnalytics,
        crashlytics = FakeCrashlytics(),
        validateTransactionForm = ValidateTransactionFormUseCaseImpl(clock = ClockOn(today)),
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

/** Nothing here writes: these tests stop at what the form holds. */
private object Unwritten : RegisterTransactionUseCase {
    override suspend operator fun invoke(
        form: TransactionForm,
        isRecurring: Boolean,
    ) = throw NotImplementedError()
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

private class FakeCards(private val cards: List<CreditCard>) : ICreditCardRepository {
    override fun observeAllCreditCards(): Flow<List<CreditCard>> = flowOf(cards)
    override suspend fun getAllCreditCards(): List<CreditCard> = cards
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

private class FakeInvoices(private val invoices: List<Invoice>) : IInvoiceRepository {
    override suspend fun getInvoicesByCreditCard(creditCardId: Long) =
        invoices.filter { it.creditCard.id == creditCardId }

    override fun observeAllInvoices(): Flow<List<Invoice>> = throw NotImplementedError()
    override fun observeInvoicesByCreditCard(creditCardId: Long): Flow<List<Invoice>> = throw NotImplementedError()
    override fun observeInvoiceById(invoiceId: Long): Flow<Invoice?> = throw NotImplementedError()
    override fun observeOpenInvoice(creditCardId: Long): Flow<Invoice?> = throw NotImplementedError()
    override fun observeAvailableInvoices(creditCardId: Long): Flow<List<Invoice>> = throw NotImplementedError()
    override fun observeUnpaidInvoice(creditCardId: Long): Flow<Invoice?> = throw NotImplementedError()
    override fun observeUnpaidInvoices(): Flow<List<Invoice>> = throw NotImplementedError()
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
    override suspend fun getAccountById(accountId: Long): Account? = null
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
