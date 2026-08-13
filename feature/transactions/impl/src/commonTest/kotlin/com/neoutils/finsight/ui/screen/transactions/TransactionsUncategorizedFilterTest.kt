@file:OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)

package com.neoutils.finsight.ui.screen.transactions

import app.cash.turbine.test
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.model.SpendingSubject
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionLabel
import com.neoutils.finsight.extension.toYearMonth
import com.neoutils.finsight.ui.icons.CategoryLazyIcon
import com.neoutils.finsight.ui.screen.transactions.TransactionsUiState.ListState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * The unclassified cut, over a month holding one of every form.
 *
 * What the tests are worth writing for is the boundary: a transfer, an invoice payment and
 * an adjustment carry no dimension on any nominal leg because they *have* no nominal leg.
 * They are outside the axis, not unclassified, and a cut that showed them would disagree
 * with the unclassified total it exists to explain.
 */
class TransactionsUncategorizedFilterTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setup() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private val month = Clock.System.now().toYearMonth()

    private val account = Account(id = 1, name = "A", type = AccountType.ASSET, currency = "BRL")
    private val savings = Account(id = 2, name = "B", type = AccountType.ASSET, currency = "BRL")
    private val cardAcc = Account(id = 200, name = "Card", type = AccountType.LIABILITY, currency = "BRL")
    private val incomeAcc = Account(id = 100, name = "income", type = AccountType.INCOME, currency = "BRL")
    private val expenseAcc = Account(id = 101, name = "expense", type = AccountType.EXPENSE, currency = "BRL")
    private val equityAcc = Account(id = 102, name = "reconciliation", type = AccountType.EQUITY, currency = "BRL")

    private val groceries = Category(
        id = 7, name = "Groceries", icon = CategoryLazyIcon("food"),
        type = Category.Type.EXPENSE, createdAt = 0L, dimensionId = 70,
    )

    private fun date(day: Int) = LocalDate(month.year, month.month, day)

    private fun entry(acc: Account, amount: Double, dimensionId: Long? = null) =
        Entry(account = acc, amount = (amount * 100).toLong(), dimensionId = dimensionId)

    private fun op(id: Long, date: LocalDate, entries: List<Entry>) =
        Transaction(id = id, title = null, date = date, entries = entries)

    private val looseExpense = op(10, date(2), listOf(entry(account, -60.0), entry(expenseAcc, 60.0)))
    private val looseIncome = op(11, date(3), listOf(entry(account, 300.0), entry(incomeAcc, -300.0)))
    private val groceriesExpense = op(
        12, date(4),
        listOf(entry(account, -40.0), entry(expenseAcc, 40.0, dimensionId = groceries.dimensionId)),
    )
    private val transfer = op(13, date(6), listOf(entry(account, -50.0), entry(savings, 50.0)))
    private val invoicePayment = op(14, date(10), listOf(entry(account, -120.0), entry(cardAcc, 120.0)))
    private val adjustment = op(15, date(12), listOf(entry(account, 25.0), entry(equityAcc, -25.0)))
    private val looseCardPurchase = op(16, date(8), listOf(entry(cardAcc, -90.0), entry(expenseAcc, 90.0)))

    /** A nominal leg tagged with a dimension no category holds — an integrity failure. */
    private val orphanExpense = op(
        17, date(14),
        listOf(entry(account, -15.0), entry(expenseAcc, 15.0, dimensionId = 999)),
    )

    private val everything = listOf(
        looseExpense, looseIncome, groceriesExpense,
        transfer, invoicePayment, adjustment, looseCardPurchase, orphanExpense,
    )

    private fun viewModel(transactions: List<Transaction> = everything) = TransactionsViewModel(
        filterLabel = null, filterTarget = null,
        transactionRepository = FakeTransactionRepository(transactions),
        categoryRepository = FakeCategoryRepository(),
        installmentRepository = NoInstallments,
        entryRepository = FakeLedger(transactions),
        consolidateMoney = consolidator(),
        observeConsolidationChanges = FakeLedger(transactions).consolidationChanges(),
        baseCurrencyRepository = FakeBaseCurrency(),
        clock = Clock.System,
    )

    private suspend fun stateAfter(
        actions: List<TransactionsAction>,
        transactions: List<Transaction> = everything,
        settled: (TransactionsUiState) -> Boolean,
    ): TransactionsUiState {
        val vm = viewModel(transactions)
        var result = TransactionsUiState()
        vm.uiState.test {
            var state = awaitItem()
            while (state.listState is ListState.Loading) state = awaitItem()

            actions.forEach { vm.onAction(it) }
            while (!settled(state)) state = awaitItem()

            result = state
            cancelAndIgnoreRemainingEvents()
        }
        return result
    }

    /** The state with the unclassified value selected, plus whatever [extra] asks for. */
    private suspend fun uncategorized(
        extra: List<TransactionsAction> = emptyList(),
        transactions: List<Transaction> = everything,
        settled: (TransactionsUiState) -> Boolean = { it.selectedSubject == SpendingSubject.Uncategorized },
    ) = stateAfter(
        actions = listOf(TransactionsAction.SelectSubject(SpendingSubject.Uncategorized)) + extra,
        transactions = transactions,
        settled = settled,
    )

    private val TransactionsUiState.listed
        get() = (listState as? ListState.Content)
            ?.transactions
            ?.values
            ?.flatten()
            ?.map { it.id }
            ?.toSet()
            .orEmpty()

    @Test
    fun `the cut holds every transaction whose nominal leg carries no dimension`() =
        runTest(dispatcher) {
            // Expense and income alike: what is asked of the leg is the same in both.
            // Everything else in the month is either classified, orphaned, or off the axis.
            assertEquals(
                setOf(looseExpense.id, looseIncome.id, looseCardPurchase.id),
                uncategorized().listed,
            )
        }

    @Test
    fun `a transfer is outside the axis and stays out of the cut`() = runTest(dispatcher) {
        val listed = uncategorized().listed

        assertEquals(false, transfer.id in listed)
        assertEquals(false, invoicePayment.id in listed)
        assertEquals(false, adjustment.id in listed, "an adjustment classifies nothing either")
    }

    @Test
    fun `a classified transaction leaves the cut`() = runTest(dispatcher) {
        assertEquals(false, groceriesExpense.id in uncategorized().listed)
    }

    @Test
    fun `an orphan dimension is not washed into the cut`() = runTest(dispatcher) {
        // It carries a dimension, so it is not an absence of classification — it is a
        // broken reference, and hiding it inside the unclassified total would bury it.
        assertEquals(false, orphanExpense.id in uncategorized().listed)
    }

    @Test
    fun `the cut composes with the nature filter`() = runTest(dispatcher) {
        val state = uncategorized(
            extra = listOf(TransactionsAction.SelectLabel(TransactionLabel.EXPENSE)),
            settled = {
                it.selectedSubject == SpendingSubject.Uncategorized &&
                    it.selectedLabel == TransactionLabel.EXPENSE
            },
        )

        assertEquals(setOf(looseExpense.id, looseCardPurchase.id), state.listed)
    }

    @Test
    fun `the cut composes with the scope`() = runTest(dispatcher) {
        val state = uncategorized(
            extra = listOf(TransactionsAction.SelectScope(TransactionScope.CARDS)),
            settled = {
                it.selectedSubject == SpendingSubject.Uncategorized &&
                    it.selectedScope == TransactionScope.CARDS
            },
        )

        assertEquals(setOf(looseCardPurchase.id), state.listed)
    }

    @Test
    fun `the summary does not move when the cut is applied`() = runTest(dispatcher) {
        val unfiltered = stateAfter(
            actions = emptyList(),
            settled = { it.listState !is ListState.Loading },
        )

        assertEquals(unfiltered.balanceOverview, uncategorized().balanceOverview)
    }

    @Test
    fun `a month with nothing unclassified offers to clear the filters`() = runTest(dispatcher) {
        val state = uncategorized(transactions = listOf(groceriesExpense, transfer))

        val listState = assertIs<ListState.EmptyScope>(state.listState)
        assertEquals(true, listState.canClearFilters)
    }

    @Test
    fun `clearing the filters returns the axis to neutral`() = runTest(dispatcher) {
        val state = uncategorized(
            extra = listOf(TransactionsAction.ClearFilters),
            settled = { it.selectedSubject == null && it.listState is ListState.Content },
        )

        assertEquals(null, state.selectedSubject)
        assertEquals(everything.map { it.id }.toSet(), state.listed)
    }
}
