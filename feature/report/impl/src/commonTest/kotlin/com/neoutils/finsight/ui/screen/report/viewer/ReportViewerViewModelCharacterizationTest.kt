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
import com.neoutils.finsight.domain.model.ContraLeg
import com.neoutils.finsight.domain.model.TransactionLeg
import com.neoutils.finsight.domain.model.ReportDocument
import com.neoutils.finsight.domain.model.ReportLayout
import com.neoutils.finsight.domain.repository.AccountFlows
import com.neoutils.finsight.domain.model.ExchangeRate
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.IBaseCurrencyRepository
import com.neoutils.finsight.domain.repository.IExchangeRateRepository
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.model.Installment
import com.neoutils.finsight.domain.repository.IInstallmentRepository
import com.neoutils.finsight.domain.repository.IInvoiceRepository
import com.neoutils.finsight.domain.repository.ITransactionRepository
import com.neoutils.finsight.domain.model.MoneyByCurrency
import com.neoutils.finsight.domain.repository.DimensionFlowsByCurrency
import com.neoutils.finsight.domain.repository.ScopeStatsByCurrency
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.usecase.CalculateReportCategorySpendingUseCase
import com.neoutils.finsight.domain.usecase.ObserveConsolidationChangesUseCase
import com.neoutils.finsight.domain.usecase.CalculateReportStatsUseCase
import com.neoutils.finsight.domain.usecase.AccountCurrencies
import com.neoutils.finsight.domain.usecase.ConsolidateMoneyUseCase
import com.neoutils.finsight.domain.usecase.GetAccountCurrenciesUseCase
import com.neoutils.finsight.extension.degradedTerm
import com.neoutils.finsight.ui.screen.report.ReportViewerParams
import com.neoutils.finsight.ui.screen.report.config.PerspectiveTab
import com.neoutils.finsight.ui.screen.report.render.ReportDocumentRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
 * the ledger aggregate (`IEntryRepository.scopeStatsByCurrency`, its semantics pinned
 * by ReportStatsQueryTest). This pins the ViewModel wiring — that the use case's
 * [ScopeStatsByCurrency] surface as `Stats.Account`.
 */
class ReportViewerViewModelCharacterizationTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setup() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private val account = Account(id = 1, name = "A", type = AccountType.ASSET, currency = "BRL")
    private val incomeAcc = Account(id = 100, name = "income", type = AccountType.INCOME, currency = "BRL")
    private val expenseAcc = Account(id = 101, name = "expense", type = AccountType.EXPENSE, currency = "BRL")

    private fun op(id: Long, date: LocalDate, entries: List<Entry>) =
        Transaction(id = id, title = null, date = date, entries = entries)

    private fun entry(acc: Account, amount: Double, dimensionId: Long? = null) =
        Entry(account = acc, amount = (amount * 100).toLong(), dimensionId = dimensionId)

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
                    stats = brlStats(income = 100.0, expense = 30.0, balance = 70.0, openingBalance = -20.0),
                ),
                accountRepository = fakes.accountRepository(listOf(account)),
                creditCardRepository = fakes.creditCardRepository(),
            ),
            calculateReportCategorySpendingUseCase = CalculateReportCategorySpendingUseCase(
                entryRepository = fakes.entryRepository(),
                categoryRepository = fakes.categoryRepository,
                accountRepository = fakes.accountRepository(listOf(account)),
                creditCardRepository = fakes.creditCardRepository(),
                consolidateMoney = fakes.consolidateMoney,
            ),
            entryRepository = fakes.entryRepository(),
            consolidateMoney = fakes.consolidateMoney,
            observeConsolidationChanges = fakes.consolidationChanges(),
            baseCurrencyRepository = fakes.baseCurrencyRepository,
            categoryRepository = fakes.categoryRepository,
            installmentRepository = NoInstallments,
            renderer = fakes.renderer,
            analytics = fakes.analytics,
        )

        vm.uiState.test {
            var state = awaitItem()
            while (state !is ReportViewerUiState.Content) state = awaitItem()
            val stats = state.stats as ReportViewerUiState.Stats.Account
            // Each figure carries the sign it is displayed with; one currency went in,
            // so the reducer hands that very figure back, exact and unmarked.
            assertEquals(100.0, stats.income.degradedTerm().value)
            assertEquals(-30.0, stats.expense.degradedTerm().value)
            assertEquals(70.0, stats.balance.degradedTerm().value)
            assertEquals(-20.0, stats.openingBalance.degradedTerm().value)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `credit card perspective sums the card legs and reads owed from the ledger`() = runTest(dispatcher) {
        val cardLiability = Account(id = 200, name = "Card", type = AccountType.LIABILITY, currency = "BRL")
        val equityAcc = Account(id = 102, name = "reconciliation", type = AccountType.EQUITY, currency = "BRL")
        val paymentSource = Account(id = 103, name = "checking", type = AccountType.ASSET, currency = "BRL")
        val card = CreditCard(
            id = 1, name = "Card", limit = 1000.0, closingDay = 5, dueDay = 15,
            accountId = cardLiability.id,
        )
        val invoice = Invoice(
            id = 1, creditCard = card, dimensionId = 1,
            openingMonth = YearMonth(2026, 2), closingMonth = YearMonth(2026, 3), dueMonth = YearMonth(2026, 4),
            status = Invoice.Status.OPEN,
        )
        val date = LocalDate(2026, 3, 10)
        // The card leg carries the invoice's dimension; the counter-leg's account type is what
        // makes the transaction an expense, an adjustment or a payment.
        fun cardOp(id: Long, cardAmount: Double, counter: Account) = op(
            id, date,
            listOf(entry(cardLiability, cardAmount, dimensionId = invoice.dimensionId), entry(counter, -cardAmount)),
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
            // The chart, not the facade: the card's LIABILITY row is what denominates
            // every figure of an invoice report (design D17).
            accountRepository = fakes.accountRepository(listOf(cardLiability)),
            creditCardRepository = fakes.creditCardRepository(listOf(card)),
            invoiceRepository = fakes.invoiceRepository(listOf(invoice)),
            calculateReportStatsUseCase = CalculateReportStatsUseCase(
                entryRepository = fakes.entryRepository(),
                accountRepository = fakes.accountRepository(listOf(cardLiability)),
                creditCardRepository = fakes.creditCardRepository(listOf(card)),
            ),
            calculateReportCategorySpendingUseCase = CalculateReportCategorySpendingUseCase(
                entryRepository = fakes.entryRepository(),
                categoryRepository = fakes.categoryRepository,
                accountRepository = fakes.accountRepository(listOf(cardLiability)),
                creditCardRepository = fakes.creditCardRepository(listOf(card)),
                consolidateMoney = fakes.consolidateMoney,
            ),
            entryRepository = fakes.entryRepository(
                owed = mapOf(1L to 70.0),
                // The invoice breakdown now reads the ledger's per-dimension flows
                // (spec `ledger-reporting`): expense 100, advance payment 30, adjustment 10.
                flows = mapOf(1L to brlFlows(expense = 100.0, advancePayment = 30.0, adjustment = 10.0)),
            ),
            consolidateMoney = fakes.consolidateMoney,
            observeConsolidationChanges = fakes.consolidationChanges(),
            baseCurrencyRepository = fakes.baseCurrencyRepository,
            categoryRepository = fakes.categoryRepository,
            installmentRepository = NoInstallments,
            renderer = fakes.renderer,
            analytics = fakes.analytics,
        )

        vm.uiState.test {
            var state = awaitItem()
            while (state !is ReportViewerUiState.Content) state = awaitItem()
            val stats = state.stats as ReportViewerUiState.Stats.Invoice
            // The invoice lines now read like the account lines of the same report:
            // spending signed negative, an advance payment positive.
            assertEquals(-100.0, stats.expense.value)
            assertEquals(30.0, stats.advancePayment.value)
            assertEquals(10.0, stats.adjustment.value)
            // Positive-as-debt, hence NATURAL and not OWED, which would zero it.
            assertEquals(70.0, stats.total.value, "owed comes from the ledger's dimensionOwed")
            cancelAndIgnoreRemainingEvents()
        }
    }
}

private class Fakes {
    fun transactionRepository(transactions: List<Transaction>) = object : ITransactionRepository {
        override fun observeAllTransactions(): Flow<List<Transaction>> = MutableStateFlow(transactions)
        override fun observeTransactionsBy(date: LocalDate?, dimensionId: Long?, accountId: Long?): Flow<List<Transaction>> = throw NotImplementedError()
        override fun observeTransactionById(id: Long): Flow<Transaction?> = throw NotImplementedError()
        override suspend fun getAllTransactions(): List<Transaction> = throw NotImplementedError()
        override suspend fun getTransactionsByIds(ids: Collection<Long>): List<Transaction> =
            throw NotImplementedError()

        override suspend fun getTransactionById(id: Long): Transaction? = throw NotImplementedError()
        override suspend fun createTransaction(intent: TransactionIntent): Transaction = throw NotImplementedError()
        override suspend fun createTransactions(intents: List<TransactionIntent>): List<Transaction> = throw NotImplementedError()
        override suspend fun updateTransaction(id: Long, title: String?, date: LocalDate, legs: List<TransactionLeg>, contra: ContraLeg?) = throw NotImplementedError()
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
        override suspend fun hasYieldingAccount(): Boolean = false
        override suspend fun getAccountCount(): Int = throw NotImplementedError()
        override suspend fun insert(account: Account): Long = throw NotImplementedError()
        override suspend fun update(account: Account) = throw NotImplementedError()
        override suspend fun delete(account: Account) = throw NotImplementedError()
        override suspend fun reopen(accountId: Long) = throw NotImplementedError()
    }

    fun creditCardRepository(cards: List<CreditCard> = emptyList()) = object : ICreditCardRepository {
        override fun observeAllCreditCards(): Flow<List<CreditCard>> = MutableStateFlow(cards)
        override suspend fun getAllCreditCards(): List<CreditCard> = cards
        override suspend fun getAllCreditCardsIncludingClosed(): List<CreditCard> = getAllCreditCards()
        override fun observeAllCreditCardsIncludingClosed(): Flow<List<CreditCard>> = observeAllCreditCards()
        override suspend fun getCreditCardById(creditCardId: Long): CreditCard? = cards.firstOrNull { it.id == creditCardId }
        override fun observeCreditCardById(creditCardId: Long): Flow<CreditCard?> = throw NotImplementedError()
        override suspend fun insert(creditCard: CreditCard, currency: String): Long = throw NotImplementedError()
        override suspend fun update(creditCard: CreditCard) = throw NotImplementedError()
        override suspend fun delete(creditCard: CreditCard) = throw NotImplementedError()
        override suspend fun unarchive(accountId: Long) = throw NotImplementedError()
        override suspend fun currencyForNewCard(): String = throw NotImplementedError()
    }

    fun invoiceRepository(invoices: List<Invoice> = emptyList()) = object : IInvoiceRepository {
        override fun observeInvoicesByCreditCard(creditCardId: Long): Flow<List<Invoice>> = MutableStateFlow(invoices)
        override fun observeAllInvoices(): Flow<List<Invoice>> = throw NotImplementedError()
        override fun observeInvoiceById(dimensionId: Long): Flow<Invoice?> = throw NotImplementedError()
        override fun observeOpenInvoice(creditCardId: Long): Flow<Invoice?> = throw NotImplementedError()
        override fun observeAvailableInvoices(creditCardId: Long): Flow<List<Invoice>> = throw NotImplementedError()
        override fun observeUnpaidInvoice(creditCardId: Long): Flow<Invoice?> = throw NotImplementedError()
        override fun observeUnpaidInvoices(): Flow<List<Invoice>> = throw NotImplementedError()
        override fun observeInvoicesToSettle(month: YearMonth): Flow<List<Invoice>> = throw NotImplementedError()
        override suspend fun getAllInvoices(): List<Invoice> = throw NotImplementedError()
        override suspend fun getInvoicesByCreditCard(creditCardId: Long): List<Invoice> = invoices
        override suspend fun getUnpaidInvoicesByCreditCard(creditCardId: Long): List<Invoice> = throw NotImplementedError()
        override suspend fun getOpenInvoice(creditCardId: Long): Invoice? = throw NotImplementedError()
        override suspend fun getInvoiceById(id: Long): Invoice? = invoices.firstOrNull { it.id == id }
        override suspend fun insert(invoice: Invoice): Invoice = throw NotImplementedError()
        override suspend fun update(invoice: Invoice) = throw NotImplementedError()
        override suspend fun deleteById(id: Long) = throw NotImplementedError()
    }

    val categoryRepository = object : ICategoryRepository {
        override fun observeAllCategories(): Flow<List<Category>> = flowOf(emptyList())
        override fun observeCategoryById(id: Long): Flow<Category?> = throw NotImplementedError()
        override suspend fun getAllCategories(): List<Category> = emptyList()
        override suspend fun getAllCategoriesIncludingClosed(): List<Category> = getAllCategories()
        override fun observeAllCategoriesIncludingClosed(): Flow<List<Category>> = observeAllCategories()
        override fun observeCategoriesByType(type: Category.Type): Flow<List<Category>> = throw NotImplementedError()
        override suspend fun getCategoryById(id: Long): Category? = throw NotImplementedError()
        override suspend fun getCategoryBySystemKey(systemKey: String): Category? = null
        override suspend fun getCategoryByDimensionId(dimensionId: Long): Category? = null
        override suspend fun archive(id: Long) = Unit
        override suspend fun unarchive(id: Long) = Unit
        override suspend fun existsByName(name: String, ignoreId: Long): Boolean = false

        override suspend fun insert(category: Category) = throw NotImplementedError()
        override suspend fun insertAll(categories: List<Category>) = throw NotImplementedError()
        override suspend fun update(category: Category) = throw NotImplementedError()
        override suspend fun delete(category: Category) = throw NotImplementedError()
    }

    fun entryRepository(
        owed: Map<Long, Double> = emptyMap(),
        stats: ScopeStatsByCurrency = ScopeStatsByCurrency.zero,
        flows: Map<Long, DimensionFlowsByCurrency> = emptyMap(),
    ) = object : IEntryRepository {
        override suspend fun getEntriesByTransaction(transactionId: Long): List<Entry> = throw NotImplementedError()
        override fun observeEntriesByTransaction(transactionId: Long): Flow<List<Entry>> = throw NotImplementedError()
        override fun observeLedgerChanges(): Flow<Unit> = flowOf(Unit)
    override suspend fun balance(accountId: Long): Double = throw NotImplementedError()
    override suspend fun hasEntries(accountId: Long): Boolean = false
    override suspend fun hasEntriesForDimension(dimensionId: Long): Boolean = false
        override suspend fun accountBalanceUpTo(accountId: Long, target: LocalDate): Double = throw NotImplementedError()
        override suspend fun balanceUpToByCurrency(target: YearMonth, excludedAccountIds: Set<Long>): MoneyByCurrency = throw NotImplementedError()
        override suspend fun naturalBalanceUpToByCurrency(target: YearMonth, type: AccountType, excludedAccountIds: Set<Long>): MoneyByCurrency = throw NotImplementedError()
        override suspend fun dimensionMonthlySeriesByCurrency(dimensionId: Long, upTo: YearMonth): Map<YearMonth, MoneyByCurrency> = throw NotImplementedError()
        override suspend fun dimensionBalanceInMonthByCurrency(month: YearMonth, dimensionId: Long): MoneyByCurrency = throw NotImplementedError()
        override suspend fun accountFlows(month: YearMonth, accountId: Long, yieldDimensionId: Long?): AccountFlows = throw NotImplementedError()
        override suspend fun owedByDimensionByCurrency(dimensionIds: Collection<Long>) =
            dimensionIds.distinct().associateWith { dimensionOwedByCurrency(it) }

        override suspend fun flowsByDimensionByCurrency(dimensionIds: Collection<Long>) =
            dimensionIds.distinct().associateWith { dimensionFlowsByCurrency(it) }

        override suspend fun dimensionOwedByCurrency(dimensionId: Long) =
            com.neoutils.finsight.domain.model.MoneyByCurrency.of("BRL", owed[dimensionId] ?: 0.0)

        override suspend fun dimensionFlowsByCurrency(dimensionId: Long) =
            flows[dimensionId] ?: DimensionFlowsByCurrency.zero

        override suspend fun liabilityMonthFlowsByCurrency(month: YearMonth) = throw NotImplementedError()
        override suspend fun assetMonthFlowsByCurrency(month: YearMonth, yieldDimensionId: Long?) = throw NotImplementedError()
        override suspend fun totalsByDimensionByCurrency(
            nominalType: AccountType,
            startDate: LocalDate,
            endDate: LocalDate,
            siblingAccountIds: List<Long>,
        ): Map<Long?, MoneyByCurrency> = throw NotImplementedError()

        override suspend fun totalsByDimensionInMonthByCurrency(
            month: YearMonth,
            nominalType: AccountType,
        ): Map<Long?, MoneyByCurrency> = throw NotImplementedError()

        override suspend fun totalsByDimensionInScopeByCurrency(
            nominalType: AccountType,
            scopeDimensionIds: List<Long>,
        ): Map<Long?, MoneyByCurrency> = throw NotImplementedError()

        override suspend fun scopeStatsByCurrency(
            scopeAccountIds: List<Long>,
            startDate: LocalDate,
            endDate: LocalDate,
        ) = stats
    }

    val baseCurrencyRepository = object : IBaseCurrencyRepository {
        private val state = MutableStateFlow("BRL")
        override fun observe(): StateFlow<String> = state
        override suspend fun set(code: String) { state.value = code }
    }

    private val exchangeRateRepository = object : IExchangeRateRepository {
        override suspend fun rateAsOf(currency: String, date: LocalDate): ExchangeRate? = null
        override suspend fun ratesAsOf(date: LocalDate): Map<String, ExchangeRate> = emptyMap()
        override suspend fun rateBetween(from: String, to: String, date: LocalDate): ExchangeRate? = null
        override fun observeAll(): Flow<List<ExchangeRate>> = flowOf(emptyList())
        override suspend fun save(rate: ExchangeRate) = throw NotImplementedError()
        override suspend fun remove(rate: ExchangeRate) = throw NotImplementedError()
        override suspend fun countNaming(currency: String) = 0
    }

    val consolidateMoney = ConsolidateMoneyUseCase(
        baseCurrencyRepository = baseCurrencyRepository,
        exchangeRateRepository = exchangeRateRepository,
        getAccountCurrencies = object : GetAccountCurrenciesUseCase {
            override suspend fun invoke() = AccountCurrencies(inUse = listOf("BRL"), ofDefaultAccount = "BRL")
        },
    )

    /** The composed trigger over the same fakes — a rate moves a figure already on screen. */
    fun consolidationChanges() = ObserveConsolidationChangesUseCase(
        entryRepository = entryRepository(),
        baseCurrencyRepository = baseCurrencyRepository,
        exchangeRateRepository = exchangeRateRepository,
    )

    val renderer = object : ReportDocumentRenderer {
        override fun render(layout: ReportLayout): ReportDocument = throw NotImplementedError()
    }

    val analytics = object : Analytics {
        override fun logScreenView(screenName: String) = Unit
        override fun logEvent(event: Event) = Unit
        override fun setUserId(id: String?) = Unit
    }
}

/** No installment badge is under test here; the list only needs the read to answer. */
private object NoInstallments : IInstallmentRepository {
    override fun observeAllInstallments(): Flow<List<Installment>> = flowOf(emptyList())
    override suspend fun getAllInstallments(): List<Installment> = emptyList()
    override suspend fun getInstallmentById(id: Long): Installment? = null
    override suspend fun createInstallment(count: Int, totalAmount: Double): Long = throw NotImplementedError()
    override suspend fun updateInstallment(id: Long, count: Int, totalAmount: Double) = throw NotImplementedError()
    override suspend fun deleteInstallmentById(id: Long) = throw NotImplementedError()
}

/** A scope's report figures, all in the one currency these tests transact in. */
private fun brlStats(
    income: Double,
    expense: Double,
    balance: Double,
    openingBalance: Double,
) = ScopeStatsByCurrency(
    income = MoneyByCurrency.of("BRL", income),
    expense = MoneyByCurrency.of("BRL", expense),
    balance = MoneyByCurrency.of("BRL", balance),
    openingBalance = MoneyByCurrency.of("BRL", openingBalance),
)

/** A sub-ledger's flows, in that same one currency. */
private fun brlFlows(
    expense: Double,
    advancePayment: Double,
    adjustment: Double,
) = DimensionFlowsByCurrency(
    expense = MoneyByCurrency.of("BRL", expense),
    advancePayment = MoneyByCurrency.of("BRL", advancePayment),
    adjustment = MoneyByCurrency.of("BRL", adjustment),
)
