@file:OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)

package com.neoutils.finsight.ui.screen.transactions

import app.cash.turbine.test
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionTarget
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
import kotlinx.datetime.minusMonth
import kotlinx.datetime.plusMonth
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * What the screen says when it has nothing to list. The two emptinesses are told apart by
 * the ledger, not by which controls are active — a month with nothing in it is a cut, not
 * a confession that the user never recorded anything — and neither may be claimed before
 * the first read lands.
 */
class TransactionsEmptyStateTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setup() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private val month = Clock.System.now().toYearMonth()
    private val previous = month.minusMonth()
    private val next = month.plusMonth()

    private val account = Account(id = 1, name = "A", type = AccountType.ASSET)
    private val cardAcc = Account(id = 200, name = "Card", type = AccountType.LIABILITY)
    private val incomeAcc = Account(id = 100, name = "income", type = AccountType.INCOME)
    private val expenseAcc = Account(id = 101, name = "expense", type = AccountType.EXPENSE)

    private val groceries = Category(
        id = 7, name = "Groceries", icon = CategoryLazyIcon("food"),
        type = Category.Type.EXPENSE, createdAt = 0L, dimensionId = 70,
    )

    private fun entry(acc: Account, amount: Double, dimensionId: Long? = null) =
        Entry(account = acc, amount = (amount * 100).toLong(), dimensionId = dimensionId)

    private fun op(id: Long, date: LocalDate, entries: List<Entry>) =
        Transaction(id = id, title = null, date = date, entries = entries)

    /** This month: one salary on an account, one purchase on a card. */
    private val salary = op(
        10, LocalDate(month.year, month.month, 2),
        listOf(entry(account, 300.0), entry(incomeAcc, -300.0)),
    )
    private val cardPurchase = op(
        11, LocalDate(month.year, month.month, 8),
        listOf(entry(cardAcc, -90.0), entry(expenseAcc, 90.0)),
    )
    private val lastMonthSalary = op(
        12, LocalDate(previous.year, previous.month, 20),
        listOf(entry(account, 500.0), entry(incomeAcc, -500.0)),
    )

    private fun viewModel(
        transactions: List<Transaction>,
        filterLabel: com.neoutils.finsight.domain.model.TransactionLabel? = null,
        category: Category? = null,
        filterTarget: TransactionTarget? = null,
    ) = TransactionsViewModel(
        filterLabel = filterLabel, category = category, filterTarget = filterTarget,
        transactionRepository = FakeTransactionRepository(transactions),
        categoryRepository = FakeCategoryRepository(),
        installmentRepository = NoInstallments,
        accountRepository = FakeAccountsForYield,
        entryRepository = FakeLedger(transactions),
    )

    /** The settled state after [actions], skipping the `Loading` initialValue. */
    private suspend fun stateAfter(
        transactions: List<Transaction>,
        actions: List<TransactionsAction> = emptyList(),
        settled: (TransactionsUiState) -> Boolean = { true },
        vm: TransactionsViewModel = viewModel(transactions),
    ): TransactionsUiState {
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

    @Test
    fun `the initial state is loading, not empty`() = runTest(dispatcher) {
        // The screen's default value is what it shows before the repository answers, and
        // it must not be mistakable for "there is nothing" — the bug this change fixes.
        assertIs<ListState.Loading>(TransactionsUiState().listState)

        viewModel(transactions = emptyList()).uiState.test {
            assertIs<ListState.Loading>(awaitItem().listState, "before the first read")

            var state = awaitItem()
            while (state.listState is ListState.Loading) state = awaitItem()
            assertIs<ListState.EmptyLedger>(state.listState, "and only then, the emptiness")

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an empty ledger is the emptiness of origin`() = runTest(dispatcher) {
        assertIs<ListState.EmptyLedger>(stateAfter(transactions = emptyList()).listState)
    }

    @Test
    fun `a month with nothing in it is a cut, not an empty ledger`() = runTest(dispatcher) {
        // Every filter is neutral here, so a state deduced from the active controls would
        // wrongly tell the user they have never recorded a transaction.
        val state = stateAfter(
            transactions = listOf(lastMonthSalary),
            actions = listOf(TransactionsAction.SelectMonth(next)),
            settled = { it.selectedYearMonth == next },
        )

        val listState = assertIs<ListState.EmptyScope>(state.listState)
        assertEquals(false, listState.canClearFilters, "there is nothing to clear")
    }

    @Test
    fun `a filter that cuts everything offers to clear`() = runTest(dispatcher) {
        val state = stateAfter(
            transactions = listOf(salary),
            actions = listOf(TransactionsAction.SelectCategory(groceries)),
            settled = { it.selectedCategory == groceries },
        )

        val listState = assertIs<ListState.EmptyScope>(state.listState)
        assertEquals(true, listState.canClearFilters)
    }

    @Test
    fun `a filter neutralised by the scope does not offer to clear`() = runTest(dispatcher) {
        // The accounts scope drops the instalment filter, so it narrows nothing and is not
        // even in the chip row: offering to clear it would promise a change it cannot make.
        val state = stateAfter(
            transactions = listOf(cardPurchase),
            actions = listOf(
                TransactionsAction.ToggleInstallment(true),
                TransactionsAction.SelectScope(TransactionScope.ACCOUNTS),
            ),
            settled = { it.selectedScope == TransactionScope.ACCOUNTS },
        )

        val listState = assertIs<ListState.EmptyScope>(state.listState, "the card purchase is outside")
        assertEquals(false, listState.canClearFilters)
    }

    @Test
    fun `the target filter neutralised by the scope does not offer to clear either`() = runTest(dispatcher) {
        val state = stateAfter(
            transactions = listOf(cardPurchase),
            actions = listOf(
                TransactionsAction.SelectTarget(TransactionTarget.CREDIT_CARD),
                TransactionsAction.SelectScope(TransactionScope.ACCOUNTS),
            ),
            settled = { it.selectedScope == TransactionScope.ACCOUNTS },
        )

        val listState = assertIs<ListState.EmptyScope>(state.listState)
        assertEquals(false, listState.canClearFilters)
    }

    @Test
    fun `a non-empty cut is content`() = runTest(dispatcher) {
        val listState = assertIs<ListState.Content>(stateAfter(listOf(salary)).listState)
        assertEquals(listOf(salary.id), listState.transactions.values.flatten().map { it.id })
    }

    @Test
    fun `clearing the filters keeps the month, the scope and the summary`() = runTest(dispatcher) {
        val vm = viewModel(listOf(salary, cardPurchase))

        val filtered = stateAfter(
            transactions = listOf(salary, cardPurchase),
            actions = listOf(
                TransactionsAction.SelectScope(TransactionScope.ACCOUNTS),
                TransactionsAction.SelectCategory(groceries),
                TransactionsAction.ToggleRecurring(true),
            ),
            settled = { it.selectedCategory == groceries && it.showRecurringOnly },
            vm = vm,
        )
        assertIs<ListState.EmptyScope>(filtered.listState)

        var cleared = filtered
        vm.uiState.test {
            var state = awaitItem()
            vm.onAction(TransactionsAction.ClearFilters)
            while (state.selectedCategory != null || state.showRecurringOnly) state = awaitItem()
            cleared = state
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(null, cleared.selectedCategory)
        assertEquals(null, cleared.selectedLabel)
        assertEquals(null, cleared.selectedTarget)
        assertEquals(false, cleared.showRecurringOnly)
        assertEquals(false, cleared.showInstallmentOnly)

        assertEquals(filtered.selectedYearMonth, cleared.selectedYearMonth, "the month is untouched")
        assertEquals(filtered.selectedScope, cleared.selectedScope, "and so is the scope")
        assertEquals(filtered.balanceOverview, cleared.balanceOverview, "so the summary cannot move")

        val listState = assertIs<ListState.Content>(cleared.listState, "and the list comes back")
        assertEquals(listOf(salary.id), listState.transactions.values.flatten().map { it.id })
    }
}
