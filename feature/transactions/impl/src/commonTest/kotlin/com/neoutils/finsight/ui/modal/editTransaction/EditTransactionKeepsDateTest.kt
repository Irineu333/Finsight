@file:OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)

package com.neoutils.finsight.ui.modal.editTransaction

import app.cash.turbine.test
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.analytics.Event
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.form.TransactionForm
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.repository.IInvoiceRepository
import com.neoutils.finsight.domain.usecase.UpdateTransactionUseCase
import com.neoutils.finsight.domain.usecase.ValidateTransactionFormUseCaseImpl
import com.neoutils.finsight.extension.currencyFormatterOf
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
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Editing is where the cascade deliberately stops.
 *
 * On a new transaction the date is a default the system chose, and an invoice may propose
 * over it. On an existing one the date is what the user wrote and the ledger kept, so
 * moving the invoice corrects the invoice and leaves the date alone — the same rule that
 * lets the projection be overridden anywhere else.
 */
class EditTransactionKeepsDateTest {

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

    private val expenseAccount = Account(
        id = 200,
        name = "Expenses",
        type = AccountType.EXPENSE,
        currency = "BRL",
    )

    private val invoice = Invoice(
        id = 1,
        creditCard = card,
        dimensionId = 7,
        openingMonth = YearMonth(2026, 3),
        closingMonth = YearMonth(2026, 4),
        dueMonth = YearMonth(2026, 4),
        status = Invoice.Status.OPEN,
    )

    /** A card purchase as the ledger holds it: the card's leg carries the invoice. */
    private val purchase = Transaction(
        id = 1,
        title = "Lunch",
        date = LocalDate(2026, 3, 11),
        entries = listOf(
            Entry(account = cardAccount, amount = -4500, dimensionId = invoice.dimensionId),
            Entry(account = expenseAccount, amount = 4500),
        ),
    )

    @Test
    fun `moving the invoice does not move the date`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.uiState.test {
            advanceUntilIdle()
            assertEquals("11/03/2026", expectMostRecentItem().form.date)

            viewModel.onAction(EditTransactionAction.SelectInvoiceMonth(YearMonth(2026, 3)))
            advanceUntilIdle()

            val state = expectMostRecentItem()
            assertEquals(YearMonth(2026, 3), state.invoiceSelection?.dueMonth, "the invoice moved")
            assertEquals("11/03/2026", state.form.date, "and the date the user wrote did not")
        }
    }

    private fun viewModel() = EditTransactionViewModel(
        transaction = purchase,
        categoryRepository = NoCategories,
        creditCardRepository = TheCard(card),
        invoiceRepository = TheInvoice(invoice),
        accountRepository = TheAccounts(cardAccount),
        updateTransaction = Unwritten,
        validateTransactionForm = ValidateTransactionFormUseCaseImpl(clock = ClockAt(today)),
        formatter = currencyFormatterOf(mapOf("BRL" to "R$")),
        modalManager = ModalManager(),
        analytics = SilentAnalytics,
        crashlytics = FakeCrashlytics(),
        clock = ClockAt(today),
    )
}

private class ClockAt(private val today: LocalDate) : Clock {
    override fun now(): Instant = today.atStartOfDayIn(TimeZone.currentSystemDefault())
}

private object SilentAnalytics : Analytics {
    override fun logScreenView(screenName: String) = Unit
    override fun logEvent(event: Event) = Unit
    override fun setUserId(id: String?) = Unit
}

/** Nothing here writes: this test stops at what the form holds. */
private object Unwritten : UpdateTransactionUseCase {
    override suspend operator fun invoke(transactionId: Long, form: TransactionForm) =
        throw NotImplementedError()
}

private object NoCategories : ICategoryRepository {
    override fun observeAllCategories(): Flow<List<Category>> = flowOf(emptyList())
    override suspend fun getCategoryByDimensionId(dimensionId: Long): Category? = null
    override fun observeAllCategoriesIncludingClosed(): Flow<List<Category>> = throw NotImplementedError()
    override fun observeCategoriesByType(type: Category.Type): Flow<List<Category>> = throw NotImplementedError()
    override fun observeCategoryById(id: Long): Flow<Category?> = throw NotImplementedError()
    override suspend fun getAllCategories(): List<Category> = throw NotImplementedError()
    override suspend fun getAllCategoriesIncludingClosed(): List<Category> = throw NotImplementedError()
    override suspend fun getCategoryById(id: Long): Category? = throw NotImplementedError()
    override suspend fun getCategoryBySystemKey(systemKey: String): Category? = throw NotImplementedError()
    override suspend fun archive(id: Long) = throw NotImplementedError()
    override suspend fun unarchive(id: Long) = throw NotImplementedError()
    override suspend fun existsByName(name: String, ignoreId: Long): Boolean = throw NotImplementedError()
    override suspend fun insert(category: Category) = throw NotImplementedError()
    override suspend fun insertAll(categories: List<Category>) = throw NotImplementedError()
    override suspend fun update(category: Category) = throw NotImplementedError()
    override suspend fun delete(category: Category) = throw NotImplementedError()
}

private class TheCard(private val card: CreditCard) : ICreditCardRepository {
    override fun observeAllCreditCards(): Flow<List<CreditCard>> = flowOf(listOf(card))
    override suspend fun getAllCreditCards(): List<CreditCard> = listOf(card)
    override suspend fun getAllCreditCardsIncludingClosed(): List<CreditCard> = listOf(card)
    override fun observeAllCreditCardsIncludingClosed(): Flow<List<CreditCard>> = throw NotImplementedError()
    override fun observeCreditCardById(creditCardId: Long): Flow<CreditCard?> = throw NotImplementedError()
    override suspend fun getCreditCardById(creditCardId: Long): CreditCard? = throw NotImplementedError()
    override suspend fun insert(creditCard: CreditCard, currency: String): Long = throw NotImplementedError()
    override suspend fun currencyForNewCard(): String = throw NotImplementedError()
    override suspend fun update(creditCard: CreditCard) = throw NotImplementedError()
    override suspend fun delete(creditCard: CreditCard) = throw NotImplementedError()
    override suspend fun unarchive(accountId: Long) = throw NotImplementedError()
}

private class TheInvoice(private val invoice: Invoice) : IInvoiceRepository {
    override suspend fun getAllInvoices(): List<Invoice> = listOf(invoice)
    override suspend fun getInvoicesByCreditCard(creditCardId: Long) =
        listOf(invoice).filter { it.creditCard.id == creditCardId }

    override fun observeAllInvoices(): Flow<List<Invoice>> = throw NotImplementedError()
    override fun observeInvoicesByCreditCard(creditCardId: Long): Flow<List<Invoice>> = throw NotImplementedError()
    override fun observeInvoiceById(invoiceId: Long): Flow<Invoice?> = throw NotImplementedError()
    override fun observeOpenInvoice(creditCardId: Long): Flow<Invoice?> = throw NotImplementedError()
    override fun observeAvailableInvoices(creditCardId: Long): Flow<List<Invoice>> = throw NotImplementedError()
    override fun observeUnpaidInvoice(creditCardId: Long): Flow<Invoice?> = throw NotImplementedError()
    override fun observeUnpaidInvoices(): Flow<List<Invoice>> = throw NotImplementedError()
    override suspend fun getUnpaidInvoicesByCreditCard(creditCardId: Long): List<Invoice> = throw NotImplementedError()
    override suspend fun getUnpaidInvoicesByCreditCards(creditCardIds: Collection<Long>): Map<Long, List<Invoice>> = throw NotImplementedError()
    override suspend fun getOpenInvoice(creditCardId: Long): Invoice? = throw NotImplementedError()
    override suspend fun getInvoiceById(id: Long): Invoice? = throw NotImplementedError()
    override suspend fun insert(invoice: Invoice): Invoice = throw NotImplementedError()
    override suspend fun update(invoice: Invoice) = throw NotImplementedError()
    override suspend fun deleteById(id: Long) = throw NotImplementedError()
}

private class TheAccounts(private val account: Account) : IAccountRepository {
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
