@file:OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)

package com.neoutils.finsight.ui.screen.transactions

import app.cash.turbine.test
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionLabel
import com.neoutils.finsight.ui.model.TransactionUi
import com.neoutils.finsight.extension.toYearMonth
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
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * The "type" axis of the transactions list filters by the transaction's **nature** —
 * `TransactionLabel`, derived by the ledger — and not by the direction of its outgoing
 * leg. One transaction of every ledger form is loaded, so the axis can be checked to
 * partition the list and to agree with the month summary above it.
 */
class TransactionsNatureFilterTest {

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

    private fun date(day: Int) = LocalDate(month.year, month.month, day)

    private fun entry(acc: Account, amount: Double) = Entry(account = acc, amount = (amount * 100).toLong())

    private fun op(id: Long, day: Int, entries: List<Entry>) =
        Transaction(id = id, title = null, date = date(day), entries = entries)

    // One transaction per ledger form. The three that the old filter got wrong — transfer
    // and payment read as "expense", both unreachable by any option — are ids 3 and 4.
    private val expense = op(1, day = 5, listOf(entry(account, -30.0), entry(expenseAcc, 30.0)))
    private val income = op(2, day = 6, listOf(entry(account, 100.0), entry(incomeAcc, -100.0)))
    private val transfer = op(3, day = 7, listOf(entry(account, -50.0), entry(savings, 50.0)))
    private val payment = op(4, day = 8, listOf(entry(account, -80.0), entry(cardAcc, 80.0)))
    private val adjustment = op(5, day = 9, listOf(entry(account, 40.0), entry(equityAcc, -40.0)))

    private val transactions = listOf(expense, income, transfer, payment, adjustment)

    private fun viewModel(filterLabel: TransactionLabel? = null) = TransactionsViewModel(
        filterLabel = filterLabel, filterTarget = null,
        transactionRepository = FakeTransactionRepository(transactions),
        categoryRepository = FakeCategoryRepository(),
        installmentRepository = NoInstallments,
        entryRepository = FakeLedger(transactions),
        consolidateMoney = consolidator(),
        observeConsolidationChanges = FakeLedger(transactions).consolidationChanges(),
            baseCurrencyRepository = FakeBaseCurrency(),
        clock = Clock.System,
    )

    /** What the screen lists: display models, already mapped (`presentation-mapping`). */
    private val TransactionsUiState.listed
        get() = (listState as? TransactionsUiState.ListState.Content)
            ?.transactions
            ?.values
            ?.flatten()
            .orEmpty()

    /** The list under [label], read off the settled state after the filter is applied. */
    private suspend fun listedUnder(label: TransactionLabel?): List<TransactionUi> {
        val vm = viewModel()
        var result = emptyList<TransactionUi>()
        vm.uiState.test {
            // Skip the Loading initialValue of stateIn; assert on the computed state.
            var state = awaitItem()
            while (state.listState is TransactionsUiState.ListState.Loading) state = awaitItem()
            if (label == null) {
                result = state.listed
            } else {
                vm.onAction(TransactionsAction.SelectLabel(label))
                while (state.selectedLabel != label) state = awaitItem()
                result = state.listed
            }
            cancelAndIgnoreRemainingEvents()
        }
        return result
    }

    @Test
    fun `each nature selects exactly its own transactions`() = runTest(dispatcher) {
        assertEquals(listOf(expense.id), listedUnder(TransactionLabel.EXPENSE).map { it.id })
        assertEquals(listOf(income.id), listedUnder(TransactionLabel.INCOME).map { it.id })
        assertEquals(listOf(transfer.id), listedUnder(TransactionLabel.TRANSFER).map { it.id })
        assertEquals(listOf(payment.id), listedUnder(TransactionLabel.PAYMENT).map { it.id })
        assertEquals(listOf(adjustment.id), listedUnder(TransactionLabel.ADJUSTMENT).map { it.id })
    }

    @Test
    fun `expense no longer lists transfers or card payments`() = runTest(dispatcher) {
        val expenses = listedUnder(TransactionLabel.EXPENSE).map { it.id }
        assertEquals(listOf(expense.id), expenses, "the two forms the old filter leaked in")
    }

    @Test
    fun `the five options partition the list`() = runTest(dispatcher) {
        val unfiltered = listedUnder(null).map { it.id }.toSet()
        val union = mutableListOf<Long>()
        TransactionLabel.entries.forEach { label ->
            union += listedUnder(label).map { it.id }
        }

        assertEquals(unfiltered.size, union.size, "no transaction is listed under two natures")
        assertEquals(unfiltered, union.toSet(), "no transaction is left out of every nature")
    }

    @Test
    fun `no filter lists every nature`() = runTest(dispatcher) {
        assertEquals(
            TransactionLabel.entries.toSet(),
            listedUnder(null).map { it.label }.toSet(),
        )
    }

    @Test
    fun `each summary line agrees with its filter`() = runTest(dispatcher) {
        // The header reads the ledger; the list reads the loaded transactions. Both must
        // agree on what composes each figure — the discrepancy this change fixes.
        assertEquals(30.0, listedUnder(TransactionLabel.EXPENSE).sumOf { it.amount.value })
        assertEquals(100.0, listedUnder(TransactionLabel.INCOME).sumOf { it.amount.value })
        assertEquals(40.0, listedUnder(TransactionLabel.ADJUSTMENT).sumOf { it.amount.value })
        assertEquals(80.0, listedUnder(TransactionLabel.PAYMENT).sumOf { it.amount.value })
    }

    @Test
    fun `the route parameter opens the list already filtered`() = runTest(dispatcher) {
        // Koin resolves this parameter by type, so a half-done rename would silently hand
        // over null and open the list unfiltered instead of failing.
        viewModel(filterLabel = TransactionLabel.PAYMENT).uiState.test {
            var state = awaitItem()
            while (state.listState is TransactionsUiState.ListState.Loading) state = awaitItem()
            assertEquals(TransactionLabel.PAYMENT, state.selectedLabel)
            assertEquals(listOf(payment.id), state.listed.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }
}
