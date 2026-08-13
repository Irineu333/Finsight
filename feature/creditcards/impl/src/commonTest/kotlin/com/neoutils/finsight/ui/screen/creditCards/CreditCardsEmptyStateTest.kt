@file:OptIn(ExperimentalTime::class, ExperimentalCoroutinesApi::class)

package com.neoutils.finsight.ui.screen.creditCards

import app.cash.turbine.test
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.ContraLeg
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.model.Installment
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.model.SpendingSubject
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionIntent
import com.neoutils.finsight.domain.model.TransactionLeg
import com.neoutils.finsight.domain.repository.AccountFlows
import com.neoutils.finsight.domain.model.MoneyByCurrency
import com.neoutils.finsight.domain.model.RecurringOccurrence
import com.neoutils.finsight.domain.repository.DimensionFlowsByCurrency
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.repository.IInstallmentRepository
import com.neoutils.finsight.domain.repository.IInvoiceRepository
import com.neoutils.finsight.domain.repository.IRecurringRepository
import com.neoutils.finsight.domain.repository.ITransactionRepository
import com.neoutils.finsight.domain.usecase.CalculateAvailableLimitUseCase
import com.neoutils.finsight.domain.usecase.CalculateInvoiceUseCase
import com.neoutils.finsight.ui.mapper.InvoiceUiMapperImpl
import com.neoutils.finsight.ui.screen.creditCards.CreditCardsUiState.ListState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import com.neoutils.finsight.testing.FakeCardAccountRepository
import com.neoutils.finsight.domain.repository.AssetMonthFlowsByCurrency
import com.neoutils.finsight.domain.repository.LiabilityMonthFlowsByCurrency
import com.neoutils.finsight.domain.repository.ScopeStatsByCurrency

/**
 * The two emptinesses of the card's transaction list — and the third, older one:
 * having no card at all, which still takes the whole screen.
 */
class CreditCardsEmptyStateTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setup() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private val card = CreditCard(
        id = 1, name = "Card", limit = 1000.0, closingDay = 5, dueDay = 15, accountId = 10,
    )

    private val invoice = Invoice(
        id = 1, creditCard = card, dimensionId = 1,
        openingMonth = YearMonth(2026, 2), closingMonth = YearMonth(2026, 3), dueMonth = YearMonth(2026, 4),
        status = Invoice.Status.OPEN,
    )

    private val cardAccount = Account(id = 10, name = "Card", type = AccountType.LIABILITY, currency = "BRL")
    private val expenseAccount = Account(id = 20, name = "Expense", type = AccountType.EXPENSE, currency = "BRL")

    private fun purchase(id: Long) = Transaction(
        id = id,
        title = "Purchase",
        date = LocalDate(2026, 3, 10),
        entries = listOf(
            Entry(transactionId = id, account = cardAccount, amount = -6_000, dimensionId = invoice.dimensionId),
            Entry(transactionId = id, account = expenseAccount, amount = 6_000, dimensionId = 77),
        ),
    )

    /** The same purchase with nothing on its nominal leg — the unclassified case. */
    private fun loosePurchase(id: Long) = Transaction(
        id = id,
        title = "Loose purchase",
        date = LocalDate(2026, 3, 11),
        entries = listOf(
            Entry(transactionId = id, account = cardAccount, amount = -3_000, dimensionId = invoice.dimensionId),
            Entry(transactionId = id, account = expenseAccount, amount = 3_000),
        ),
    )

    private fun viewModel(
        cards: List<CreditCard>,
        transactions: List<Transaction>,
    ): CreditCardsViewModel {
        val invoices = if (cards.isEmpty()) emptyList() else listOf(invoice)
        val invoiceRepository = FakeInvoiceRepository(invoices)
        val calculateInvoice = CalculateInvoiceUseCase(FlatEntryRepository)

        return CreditCardsViewModel(
            entryRepository = FlatEntryRepository,
            accountRepository = FakeCardAccountRepository(),
            recurringRepository = NoRecurring,
            creditCardRepository = FakeCreditCardRepository(cards),
            transactionRepository = FakeTransactionRepository(transactions),
            invoiceRepository = invoiceRepository,
            categoryRepository = FakeCategoryRepository(),
            installmentRepository = NoInstallments,
            invoiceUiMapper = InvoiceUiMapperImpl(
                calculateInvoiceUseCase = calculateInvoice,
                calculateAvailableLimitUseCase = CalculateAvailableLimitUseCase(
                    invoiceRepository = invoiceRepository,
                    calculateInvoiceUseCase = calculateInvoice,
                ),
                accountRepository = FakeCardAccountRepository(),
                clock = Clock.System,
            ),
        )
    }

    @Test
    fun `no card at all still takes the whole screen`() = runTest(dispatcher) {
        viewModel(cards = emptyList(), transactions = emptyList()).uiState.test {
            var state = awaitItem()
            while (state == CreditCardsUiState.Loading) state = awaitItem()
            assertEquals(CreditCardsUiState.Empty, state)
        }
    }

    @Test
    fun `an invoice with nothing on it reads as an empty invoice`() = runTest(dispatcher) {
        viewModel(cards = listOf(card), transactions = emptyList()).uiState.test {
            assertEquals(ListState.EmptyInvoice, awaitListState())
        }
    }

    @Test
    fun `the unclassified cut keeps the purchase whose nominal leg carries no dimension`() =
        runTest(dispatcher) {
            val vm = viewModel(
                cards = listOf(card),
                transactions = listOf(purchase(id = 1), loosePurchase(id = 2)),
            )

            vm.uiState.test {
                assertIs<ListState.Content>(awaitListState())

                vm.onAction(CreditCardsAction.SelectSubject(SpendingSubject.Uncategorized))

                val listState = assertIs<ListState.Content>(
                    awaitListState { it is ListState.Content && it.transactions.values.flatten().size == 1 }
                )
                assertEquals(
                    listOf(2L),
                    listState.transactions.values.flatten().map { it.id },
                )
            }
        }

    @Test
    fun `a filter that cuts everything offers to clear and clearing brings the list back`() = runTest(dispatcher) {
        val vm = viewModel(cards = listOf(card), transactions = listOf(purchase(id = 1)))

        vm.uiState.test {
            assertIs<ListState.Content>(awaitListState())

            vm.onAction(CreditCardsAction.ToggleInstallment(enabled = true))

            val listState = assertIs<ListState.EmptyScope>(awaitListState { it is ListState.EmptyScope })
            assertEquals(true, listState.canClearFilters)

            vm.onAction(CreditCardsAction.ClearFilters)

            assertIs<ListState.Content>(awaitListState { it is ListState.Content })
        }
    }
}

private suspend fun app.cash.turbine.TurbineTestContext<CreditCardsUiState>.awaitListState(
    predicate: (ListState) -> Boolean = { true },
): ListState {
    var state = awaitItem()
    while (state !is CreditCardsUiState.Content || !predicate(state.listState)) state = awaitItem()
    return state.listState
}

private class FakeCreditCardRepository(private val cards: List<CreditCard>) : ICreditCardRepository {
    override fun observeAllCreditCards(): Flow<List<CreditCard>> = MutableStateFlow(cards)
    override fun observeAllCreditCardsIncludingClosed(): Flow<List<CreditCard>> = observeAllCreditCards()
    override fun observeCreditCardById(creditCardId: Long): Flow<CreditCard?> =
        MutableStateFlow(cards.firstOrNull { it.id == creditCardId })

    override suspend fun getAllCreditCards(): List<CreditCard> = cards
    override suspend fun getAllCreditCardsIncludingClosed(): List<CreditCard> = cards
    override suspend fun getCreditCardById(creditCardId: Long): CreditCard? = cards.firstOrNull { it.id == creditCardId }
    override suspend fun insert(creditCard: CreditCard, currency: String): Long = throw NotImplementedError()
    override suspend fun update(creditCard: CreditCard) = throw NotImplementedError()
    override suspend fun delete(creditCard: CreditCard) = throw NotImplementedError()
    override suspend fun unarchive(accountId: Long) = throw NotImplementedError()
    override suspend fun currencyForNewCard(): String = throw NotImplementedError()
}

private class FakeInvoiceRepository(private val invoices: List<Invoice>) : IInvoiceRepository {
    override fun observeUnpaidInvoices(): Flow<List<Invoice>> = MutableStateFlow(invoices)
    override fun observeInvoicesByCreditCard(creditCardId: Long): Flow<List<Invoice>> = MutableStateFlow(invoices)
    override suspend fun getInvoicesByCreditCard(creditCardId: Long): List<Invoice> = invoices
    override suspend fun getUnpaidInvoicesByCreditCard(creditCardId: Long): List<Invoice> = invoices
    override suspend fun getInvoiceById(id: Long): Invoice? = invoices.firstOrNull { it.id == id }
    override fun observeAllInvoices(): Flow<List<Invoice>> = MutableStateFlow(invoices)
    override fun observeInvoiceById(dimensionId: Long): Flow<Invoice?> = throw NotImplementedError()
    override fun observeOpenInvoice(creditCardId: Long): Flow<Invoice?> = throw NotImplementedError()
    override fun observeAvailableInvoices(creditCardId: Long): Flow<List<Invoice>> = throw NotImplementedError()
    override fun observeUnpaidInvoice(creditCardId: Long): Flow<Invoice?> = throw NotImplementedError()
    override suspend fun getAllInvoices(): List<Invoice> = invoices
    override suspend fun getOpenInvoice(creditCardId: Long): Invoice? = invoices.firstOrNull { it.status.isOpen }
    override suspend fun insert(invoice: Invoice): Invoice = throw NotImplementedError()
    override suspend fun update(invoice: Invoice) = throw NotImplementedError()
    override suspend fun deleteById(id: Long) = throw NotImplementedError()
}

private class FakeTransactionRepository(private val transactions: List<Transaction>) : ITransactionRepository {
    override fun observeTransactionsBy(date: LocalDate?, dimensionId: Long?, accountId: Long?): Flow<List<Transaction>> =
        MutableStateFlow(transactions)

    override fun observeAllTransactions(): Flow<List<Transaction>> = MutableStateFlow(transactions)
    override fun observeTransactionById(id: Long): Flow<Transaction?> = throw NotImplementedError()
    override suspend fun getAllTransactions(): List<Transaction> = transactions
    override suspend fun getTransactionById(id: Long): Transaction? = transactions.firstOrNull { it.id == id }
    override suspend fun createTransaction(intent: TransactionIntent): Transaction = throw NotImplementedError()
    override suspend fun createTransactions(intents: List<TransactionIntent>): List<Transaction> = throw NotImplementedError()
    override suspend fun updateTransaction(id: Long, title: String?, date: LocalDate, leg: TransactionLeg, contra: ContraLeg?) =
        throw NotImplementedError()

    override suspend fun deleteTransactionsByIds(ids: List<Long>) = throw NotImplementedError()
    override suspend fun deleteTransactionById(id: Long) = throw NotImplementedError()
}

private class FakeCategoryRepository : ICategoryRepository {
    override fun observeAllCategories(): Flow<List<Category>> = MutableStateFlow(emptyList())
    override fun observeAllCategoriesIncludingClosed(): Flow<List<Category>> = observeAllCategories()
    override fun observeCategoryById(id: Long): Flow<Category?> = throw NotImplementedError()
    override suspend fun getAllCategories(): List<Category> = emptyList()
    override suspend fun getAllCategoriesIncludingClosed(): List<Category> = emptyList()
    override fun observeCategoriesByType(type: Category.Type): Flow<List<Category>> = throw NotImplementedError()
    override suspend fun getCategoryById(id: Long): Category? = null
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

private object NoInstallments : IInstallmentRepository {
    override fun observeAllInstallments(): Flow<List<Installment>> = flowOf(emptyList())
    override suspend fun getAllInstallments(): List<Installment> = emptyList()
    override suspend fun getInstallmentById(id: Long): Installment? = null
    override suspend fun createInstallment(count: Int, totalAmount: Double): Long = throw NotImplementedError()
    override suspend fun updateInstallment(id: Long, count: Int, totalAmount: Double) = throw NotImplementedError()
    override suspend fun deleteInstallmentById(id: Long) = throw NotImplementedError()
}

private object NoRecurring : IRecurringRepository {
    override fun observeAllRecurring(): Flow<List<Recurring>> = flowOf(emptyList())
    override fun observeRecurringById(id: Long): Flow<Recurring?> = throw NotImplementedError()
    override suspend fun getRecurringById(id: Long): Recurring? = null
    override suspend fun hasRecurringForAccount(accountId: Long): Boolean = false
    override suspend fun hasRecurringForCreditCard(creditCardId: Long): Boolean = false
    override suspend fun hasRecurringForCategory(categoryId: Long): Boolean = false
    override suspend fun hasTransactionForRecurring(recurringId: Long): Boolean = false
    override suspend fun insert(recurring: Recurring) = throw NotImplementedError()
    override suspend fun createWithFirstCycle(
        recurring: Recurring,
        firstCycle: TransactionIntent,
        occurrence: RecurringOccurrence,
    ): Transaction = throw NotImplementedError()
    override suspend fun update(recurring: Recurring) = throw NotImplementedError()
    override suspend fun delete(recurring: Recurring) = throw NotImplementedError()
}

/** No figure is under test here; the card at the top only needs the reads to answer. */
private object FlatEntryRepository : IEntryRepository {
    override suspend fun getEntriesByTransaction(transactionId: Long): List<Entry> = emptyList()
    override fun observeEntriesByTransaction(transactionId: Long): Flow<List<Entry>> = flowOf(emptyList())
    override fun observeLedgerChanges(): Flow<Unit> = flowOf(Unit)
    override suspend fun balance(accountId: Long): Double = 0.0
    override suspend fun hasEntries(accountId: Long): Boolean = false
    override suspend fun hasEntriesForDimension(dimensionId: Long): Boolean = false
    override suspend fun accountFlows(month: YearMonth, accountId: Long, yieldDimensionId: Long?) = AccountFlows("BRL", 0.0, 0.0, 0.0, 0.0, 0.0)
    override suspend fun dimensionEntryCountInMonth(month: YearMonth, dimensionId: Long): Int = 0
    override suspend fun dimensionOwedByCurrency(dimensionId: Long) = MoneyByCurrency.of("BRL", 0.0)
    override suspend fun dimensionFlowsByCurrency(dimensionId: Long) = DimensionFlowsByCurrency(
        expense = MoneyByCurrency.of("BRL", 0.0),
        advancePayment = MoneyByCurrency.of("BRL", 0.0),
        adjustment = MoneyByCurrency.of("BRL", 0.0),
    )

    override suspend fun accountBalanceUpTo(accountId: Long, target: LocalDate): Double = throw NotImplementedError()
    override suspend fun balanceUpToByCurrency(target: YearMonth, excludedAccountIds: Set<Long>): MoneyByCurrency = throw NotImplementedError()
    override suspend fun naturalBalanceUpToByCurrency(target: YearMonth, type: AccountType, excludedAccountIds: Set<Long>): MoneyByCurrency = throw NotImplementedError()
    override suspend fun dimensionBalanceInMonthByCurrency(month: YearMonth, dimensionId: Long): MoneyByCurrency = throw NotImplementedError()
    override suspend fun owedByDimensionByCurrency(dimensionIds: Collection<Long>): Map<Long, MoneyByCurrency> = throw NotImplementedError()
    override suspend fun flowsByDimensionByCurrency(dimensionIds: Collection<Long>): Map<Long, DimensionFlowsByCurrency> = throw NotImplementedError()
    override suspend fun liabilityMonthFlowsByCurrency(month: YearMonth): LiabilityMonthFlowsByCurrency = throw NotImplementedError()
    override suspend fun assetMonthFlowsByCurrency(month: YearMonth, yieldDimensionId: Long?): AssetMonthFlowsByCurrency = throw NotImplementedError()
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
    ): ScopeStatsByCurrency = throw NotImplementedError()
}
