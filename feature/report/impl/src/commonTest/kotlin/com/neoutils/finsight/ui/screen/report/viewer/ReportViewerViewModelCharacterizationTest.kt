@file:OptIn(ExperimentalCoroutinesApi::class)

package com.neoutils.finsight.ui.screen.report.viewer

import app.cash.turbine.test
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.analytics.Event
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionIntent
import com.neoutils.finsight.domain.model.TransactionLeg
import com.neoutils.finsight.domain.model.ReportDocument
import com.neoutils.finsight.domain.model.ReportLayout
import com.neoutils.finsight.domain.repository.AccountFlows
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.repository.IInvoiceRepository
import com.neoutils.finsight.domain.repository.ITransactionRepository
import com.neoutils.finsight.domain.repository.ReportStats
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.usecase.CalculateReportCategorySpendingUseCase
import com.neoutils.finsight.domain.usecase.CalculateReportStatsUseCase
import com.neoutils.finsight.ui.screen.report.ReportViewerParams
import com.neoutils.finsight.ui.screen.report.config.PerspectiveTab
import com.neoutils.finsight.ui.screen.report.render.ReportDocumentRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
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

/**
 * Characterizes the account-perspective stats of [ReportViewerViewModel] (sites
 * :84,87,90): it forwards [CalculateReportStatsUseCase], whose figures now come from
 * the ledger aggregate (`IEntryRepository.reportStats`, its semantics pinned by
 * ReportStatsQueryTest). This pins the ViewModel wiring — that the use case's
 * [ReportStats] surface as `Stats.Account`.
 */
class ReportViewerViewModelCharacterizationTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setup() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private val account = Account(id = 1, name = "A", type = AccountType.ASSET)
    private val incomeAcc = Account(id = 100, name = "income", type = AccountType.INCOME)
    private val expenseAcc = Account(id = 101, name = "expense", type = AccountType.EXPENSE)

    private fun op(id: Long, date: LocalDate, entries: List<Entry>) =
        Transaction(id = id, title = null, date = date, entries = entries)

    private fun entry(acc: Account, amount: Double, invoiceId: Long? = null) =
        Entry(account = acc, amount = (amount * 100).toLong(), invoiceId = invoiceId)

    // The account-perspective stats now read the ledger legs (task 4.6). Each transaction
    // carries its balanced entries; the report figures derive from those.
    private fun accountEntries(counter: Account, assetAmount: Double, counterAmount: Double) =
        listOf(entry(account, assetAmount), entry(counter, counterAmount))

    @Test
    fun `account perspective forwards the report stats`() = runTest(dispatcher) {
        val transactions = listOf(
            op(1, LocalDate(2026, 3, 5), accountEntries(incomeAcc, 100.0, -100.0)),
            op(2, LocalDate(2026, 3, 10), accountEntries(expenseAcc, -30.0, 30.0)),
            op(3, LocalDate(2026, 2, 10), accountEntries(expenseAcc, -20.0, 20.0)), // prior → opening
        )
        val fakes = Fakes()
        val vm = ReportViewerViewModel(
            params = ReportViewerParams(
                perspectiveType = PerspectiveTab.ACCOUNT,
                accountIds = listOf(1),
                startDate = LocalDate(2026, 3, 1),
                endDate = LocalDate(2026, 3, 31),
                includeSpendingByCategory = false,
                includeIncomeByCategory = false,
                includeTransactionList = false,
            ),
            transactionRepository = fakes.transactionRepository(transactions),
            accountRepository = fakes.accountRepository(listOf(account)),
            creditCardRepository = fakes.creditCardRepository(),
            invoiceRepository = fakes.invoiceRepository(),
            calculateReportStatsUseCase = CalculateReportStatsUseCase(
                entryRepository = fakes.entryRepository(
                    stats = ReportStats(income = 100.0, expense = 30.0, balance = 70.0, openingBalance = -20.0),
                ),
                accountRepository = fakes.accountRepository(listOf(account)),
                creditCardRepository = fakes.creditCardRepository(),
            ),
            calculateReportCategorySpendingUseCase = CalculateReportCategorySpendingUseCase(
                entryRepository = fakes.entryRepository(),
                categoryRepository = fakes.categoryRepository,
                accountRepository = fakes.accountRepository(listOf(account)),
                creditCardRepository = fakes.creditCardRepository(),
            ),
            entryRepository = fakes.entryRepository(),
            renderer = fakes.renderer,
            analytics = fakes.analytics,
        )

        vm.uiState.test {
            var state = awaitItem()
            while (state !is ReportViewerUiState.Content) state = awaitItem()
            val stats = state.stats as ReportViewerUiState.Stats.Account
            assertEquals(100.0, stats.income)
            assertEquals(30.0, stats.expense)
            assertEquals(70.0, stats.balance)
            assertEquals(-20.0, stats.openingBalance)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `credit card perspective sums the card legs and reads owed from the ledger`() = runTest(dispatcher) {
        val cardLiability = Account(id = 200, name = "Card", type = AccountType.LIABILITY)
        val equityAcc = Account(id = 102, name = "reconciliation", type = AccountType.EQUITY)
        val paymentSource = Account(id = 103, name = "checking", type = AccountType.ASSET)
        val card = CreditCard(
            id = 1, name = "Card", limit = 1000.0, closingDay = 5, dueDay = 15,
            accountId = cardLiability.id,
        )
        val invoice = Invoice(
            id = 1, creditCard = card,
            openingMonth = YearMonth(2026, 2), closingMonth = YearMonth(2026, 3), dueMonth = YearMonth(2026, 4),
            status = Invoice.Status.OPEN,
        )
        val date = LocalDate(2026, 3, 10)
        // The card leg carries the invoice id; the counter-leg's account type is what
        // makes the transaction an expense, an adjustment or a payment.
        fun cardOp(id: Long, cardAmount: Double, counter: Account) = op(
            id, date,
            listOf(entry(cardLiability, cardAmount, invoiceId = invoice.id), entry(counter, -cardAmount)),
        )
        val transactions = listOf(
            cardOp(1, -60.0, expenseAcc),
            cardOp(2, -40.0, expenseAcc),
            cardOp(3, 10.0, equityAcc),
            cardOp(4, 30.0, paymentSource), // advance payment
        )
        val fakes = Fakes()
        val vm = ReportViewerViewModel(
            params = ReportViewerParams(
                perspectiveType = PerspectiveTab.CREDIT_CARD,
                creditCardId = 1,
                invoiceIds = listOf(1),
                startDate = LocalDate(2026, 3, 1),
                endDate = LocalDate(2026, 3, 31),
                includeSpendingByCategory = false,
                includeIncomeByCategory = false,
                includeTransactionList = false,
            ),
            transactionRepository = fakes.transactionRepository(transactions),
            accountRepository = fakes.accountRepository(emptyList()),
            creditCardRepository = fakes.creditCardRepository(listOf(card)),
            invoiceRepository = fakes.invoiceRepository(listOf(invoice)),
            calculateReportStatsUseCase = CalculateReportStatsUseCase(
                entryRepository = fakes.entryRepository(),
                accountRepository = fakes.accountRepository(emptyList()),
                creditCardRepository = fakes.creditCardRepository(listOf(card)),
            ),
            calculateReportCategorySpendingUseCase = CalculateReportCategorySpendingUseCase(
                entryRepository = fakes.entryRepository(),
                categoryRepository = fakes.categoryRepository,
                accountRepository = fakes.accountRepository(emptyList()),
                creditCardRepository = fakes.creditCardRepository(listOf(card)),
            ),
            entryRepository = fakes.entryRepository(owed = mapOf(1L to 70.0)),
            renderer = fakes.renderer,
            analytics = fakes.analytics,
        )

        vm.uiState.test {
            var state = awaitItem()
            while (state !is ReportViewerUiState.Content) state = awaitItem()
            val stats = state.stats as ReportViewerUiState.Stats.Invoice
            assertEquals(100.0, stats.expense)
            assertEquals(30.0, stats.advancePayment)
            assertEquals(10.0, stats.adjustment)
            assertEquals(70.0, stats.total, "owed comes from the ledger's invoiceOwed")
            cancelAndIgnoreRemainingEvents()
        }
    }
}

private class Fakes {
    fun transactionRepository(transactions: List<Transaction>) = object : ITransactionRepository {
        override fun observeAllTransactions(): Flow<List<Transaction>> = MutableStateFlow(transactions)
        override fun observeTransactionsBy(date: LocalDate?, invoiceId: Long?, creditCardId: Long?, accountId: Long?): Flow<List<Transaction>> = throw NotImplementedError()
        override fun observeTransactionById(id: Long): Flow<Transaction?> = throw NotImplementedError()
        override suspend fun getAllTransactions(): List<Transaction> = throw NotImplementedError()
        override suspend fun getTransactionById(id: Long): Transaction? = throw NotImplementedError()
        override suspend fun createTransaction(intent: TransactionIntent): Transaction = throw NotImplementedError()
        override suspend fun createTransactions(intents: List<TransactionIntent>): List<Transaction> = throw NotImplementedError()
        override suspend fun updateTransaction(id: Long, title: String?, date: LocalDate, leg: TransactionLeg) = throw NotImplementedError()
        override suspend fun deleteTransactionById(id: Long) = throw NotImplementedError()
        override suspend fun deleteTransactionsByIds(ids: List<Long>) = throw NotImplementedError()
    }

    fun accountRepository(accounts: List<Account>) = object : IAccountRepository {
        override fun observeAllAccounts(): Flow<List<Account>> = MutableStateFlow(accounts)
        override suspend fun getAllAccounts(): List<Account> = accounts
        override suspend fun getAllAccountsIncludingClosed(): List<Account> = accounts
        override fun observeAllAccountsIncludingClosed(): Flow<List<Account>> = flowOf(accounts)
        override suspend fun getAllLedgerAccounts(): List<Account> = accounts
        override fun observeAllLedgerAccounts(): Flow<List<Account>> = MutableStateFlow(accounts)
        override suspend fun getAccountById(accountId: Long): Account? = accounts.firstOrNull { it.id == accountId }
        override fun observeAccountById(accountId: Long): Flow<Account?> = throw NotImplementedError()
        override suspend fun getDefaultAccount(): Account? = throw NotImplementedError()
        override fun observeDefaultAccount(): Flow<Account?> = throw NotImplementedError()
        override suspend fun getAccountCount(): Int = throw NotImplementedError()
        override suspend fun insert(account: Account): Long = throw NotImplementedError()
        override suspend fun update(account: Account) = throw NotImplementedError()
        override suspend fun delete(account: Account) = throw NotImplementedError()
    }

    fun creditCardRepository(cards: List<CreditCard> = emptyList()) = object : ICreditCardRepository {
        override fun observeAllCreditCards(): Flow<List<CreditCard>> = MutableStateFlow(cards)
        override suspend fun getAllCreditCards(): List<CreditCard> = cards
        override suspend fun getAllCreditCardsIncludingClosed(): List<CreditCard> = getAllCreditCards()
        override fun observeAllCreditCardsIncludingClosed(): Flow<List<CreditCard>> = observeAllCreditCards()
        override suspend fun getCreditCardById(creditCardId: Long): CreditCard? = cards.firstOrNull { it.id == creditCardId }
        override fun observeCreditCardById(creditCardId: Long): Flow<CreditCard?> = throw NotImplementedError()
        override suspend fun insert(creditCard: CreditCard): Long = throw NotImplementedError()
        override suspend fun update(creditCard: CreditCard) = throw NotImplementedError()
        override suspend fun delete(creditCard: CreditCard) = throw NotImplementedError()
    }

    fun invoiceRepository(invoices: List<Invoice> = emptyList()) = object : IInvoiceRepository {
        override fun observeInvoicesByCreditCard(creditCardId: Long): Flow<List<Invoice>> = MutableStateFlow(invoices)
        override fun observeAllInvoices(): Flow<List<Invoice>> = throw NotImplementedError()
        override fun observeInvoiceById(invoiceId: Long): Flow<Invoice?> = throw NotImplementedError()
        override fun observeOpenInvoice(creditCardId: Long): Flow<Invoice?> = throw NotImplementedError()
        override fun observeAvailableInvoices(creditCardId: Long): Flow<List<Invoice>> = throw NotImplementedError()
        override fun observeUnpaidInvoice(creditCardId: Long): Flow<Invoice?> = throw NotImplementedError()
        override fun observeUnpaidInvoices(): Flow<List<Invoice>> = throw NotImplementedError()
        override suspend fun getAllInvoices(): List<Invoice> = throw NotImplementedError()
        override suspend fun getInvoicesByCreditCard(creditCardId: Long): List<Invoice> = invoices
        override suspend fun getUnpaidInvoicesByCreditCard(creditCardId: Long): List<Invoice> = throw NotImplementedError()
        override suspend fun getOpenInvoice(creditCardId: Long): Invoice? = throw NotImplementedError()
        override suspend fun getInvoiceById(id: Long): Invoice? = invoices.firstOrNull { it.id == id }
        override suspend fun insert(invoice: Invoice): Long = throw NotImplementedError()
        override suspend fun update(invoice: Invoice) = throw NotImplementedError()
        override suspend fun deleteById(id: Long) = throw NotImplementedError()
    }

    val categoryRepository = object : ICategoryRepository {
        override fun observeAllCategories(): Flow<List<Category>> = throw NotImplementedError()
        override fun observeCategoryById(id: Long): Flow<Category?> = throw NotImplementedError()
        override suspend fun getAllCategories(): List<Category> = throw NotImplementedError()
        override suspend fun getAllCategoriesIncludingClosed(): List<Category> = getAllCategories()
        override fun observeAllCategoriesIncludingClosed(): Flow<List<Category>> = observeAllCategories()
        override fun observeCategoriesByType(type: Category.Type): Flow<List<Category>> = throw NotImplementedError()
        override suspend fun getCategoryById(id: Long): Category? = throw NotImplementedError()
        override suspend fun insert(category: Category) = throw NotImplementedError()
        override suspend fun update(category: Category) = throw NotImplementedError()
        override suspend fun delete(category: Category) = throw NotImplementedError()
    }

    fun entryRepository(
        owed: Map<Long, Double> = emptyMap(),
        stats: ReportStats = ReportStats(0.0, 0.0, 0.0, 0.0),
    ) = object : IEntryRepository {
        override suspend fun getEntriesByTransaction(transactionId: Long): List<Entry> = throw NotImplementedError()
        override fun observeEntriesByTransaction(transactionId: Long): Flow<List<Entry>> = throw NotImplementedError()
        override fun observeLedgerChanges(): Flow<Unit> = flowOf(Unit)
    override suspend fun balance(accountId: Long): Double = throw NotImplementedError()
    override suspend fun hasEntries(accountId: Long): Boolean = false
        override suspend fun balanceUpTo(target: YearMonth, accountId: Long?): Double = throw NotImplementedError()
        override suspend fun balanceInMonth(month: YearMonth, accountId: Long): Double = throw NotImplementedError()
        override suspend fun accountFlows(month: YearMonth, accountId: Long): AccountFlows = throw NotImplementedError()
        override suspend fun entryCountInMonth(month: YearMonth, accountId: Long): Int = throw NotImplementedError()
        override suspend fun invoiceOwed(invoiceId: Long): Double = owed[invoiceId] ?: 0.0
        override suspend fun invoiceFlows(invoiceId: Long): com.neoutils.finsight.domain.repository.InvoiceFlows = throw NotImplementedError()
        override suspend fun cardMonthFlows(month: YearMonth): com.neoutils.finsight.domain.repository.CardMonthFlows = throw NotImplementedError()
        override suspend fun netWorth(): Double = throw NotImplementedError()
        override suspend fun categoryTotals(categoryType: AccountType, startDate: LocalDate, endDate: LocalDate, siblingAccountIds: List<Long>): Map<Long, Double> = throw NotImplementedError()
        override suspend fun categoryTotalsForInvoices(categoryType: AccountType, invoiceIds: List<Long>): Map<Long, Double> = throw NotImplementedError()
        override suspend fun reportStats(scopeAccountIds: List<Long>, startDate: LocalDate, endDate: LocalDate): ReportStats = stats
    }

    val renderer = object : ReportDocumentRenderer {
        override fun render(layout: ReportLayout): ReportDocument = throw NotImplementedError()
    }

    val analytics = object : Analytics {
        override fun logScreenView(screenName: String) = Unit
        override fun logEvent(event: Event) = Unit
        override fun setUserId(id: String?) = Unit
    }
}
