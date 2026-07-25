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
import com.neoutils.finsight.ui.screen.transactions.TransactionsUiState.BalanceOverview
import com.neoutils.finsight.ui.screen.transactions.TransactionsUiState.ListState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minusMonth
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * The screen's central promise: whatever the scope, the summary reconciles the perimeter
 * the list is showing. Each test states the identity of one scope —
 * `closing = opening + Σ displayed flows` — over a ledger that actually holds the
 * transactions, so a wrong composition cannot pass by agreeing with itself.
 */
class TransactionScopeTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setup() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private val month = Clock.System.now().toYearMonth()
    private val previous = month.minusMonth()

    private val account = Account(id = 1, name = "A", type = AccountType.ASSET)
    private val savings = Account(id = 2, name = "B", type = AccountType.ASSET)
    private val cardAcc = Account(id = 200, name = "Card", type = AccountType.LIABILITY)
    private val incomeAcc = Account(id = 100, name = "income", type = AccountType.INCOME)
    private val expenseAcc = Account(id = 101, name = "expense", type = AccountType.EXPENSE)
    private val equityAcc = Account(id = 102, name = "reconciliation", type = AccountType.EQUITY)

    private val groceries = Category(
        id = 7, name = "Groceries", icon = CategoryLazyIcon("food"),
        type = Category.Type.EXPENSE, createdAt = 0L, dimensionId = 70,
    )

    private fun date(day: Int) = LocalDate(month.year, month.month, day)
    private fun previousDate(day: Int) = LocalDate(previous.year, previous.month, day)

    private fun entry(acc: Account, amount: Double, dimensionId: Long? = null) =
        Entry(account = acc, amount = (amount * 100).toLong(), dimensionId = dimensionId)

    private fun op(id: Long, date: LocalDate, entries: List<Entry>) =
        Transaction(id = id, title = null, date = date, entries = entries)

    // A month holding one of every form, so no scope's identity can be satisfied by a
    // ledger that simply lacks the case that would break it.
    private val openingSalary = op(1, previousDate(20), listOf(entry(account, 500.0), entry(incomeAcc, -500.0)))
    private val openingPurchase = op(2, previousDate(22), listOf(entry(cardAcc, -120.0), entry(expenseAcc, 120.0)))

    private val salary = op(10, date(2), listOf(entry(account, 300.0), entry(incomeAcc, -300.0)))
    private val groceriesExpense = op(
        11, date(4),
        listOf(entry(account, -60.0), entry(expenseAcc, 60.0, dimensionId = groceries.dimensionId))
    )
    private val transfer = op(12, date(6), listOf(entry(account, -50.0), entry(savings, 50.0)))
    private val cardPurchase = op(13, date(8), listOf(entry(cardAcc, -90.0), entry(expenseAcc, 90.0)))
    private val invoicePayment = op(14, date(10), listOf(entry(account, -120.0), entry(cardAcc, 120.0)))
    private val accountAdjustment = op(15, date(12), listOf(entry(account, 25.0), entry(equityAcc, -25.0)))
    private val invoiceAdjustment = op(16, date(14), listOf(entry(cardAcc, 15.0), entry(equityAcc, -15.0)))

    private val everything = listOf(
        openingSalary, openingPurchase,
        salary, groceriesExpense, transfer, cardPurchase,
        invoicePayment, accountAdjustment, invoiceAdjustment,
    )

    private fun viewModel(transactions: List<Transaction> = everything) = TransactionsViewModel(
        filterLabel = null, category = null, filterTarget = null,
        transactionRepository = FakeTransactionRepository(transactions),
        categoryRepository = FakeCategoryRepository(),
        installmentRepository = NoInstallments,
        entryRepository = FakeLedger(transactions),
    )

    /**
     * The settled state under [scope]. [settled] tells the collector when every action
     * has landed, since the scope alone cannot: the screen already opens on the overall
     * one, so selecting it emits nothing to wait for.
     */
    private suspend fun stateUnder(
        scope: TransactionScope,
        transactions: List<Transaction> = everything,
        actions: List<TransactionsAction> = emptyList(),
        settled: (TransactionsUiState) -> Boolean = { it.selectedScope == scope },
    ): TransactionsUiState {
        val vm = viewModel(transactions)
        var result = TransactionsUiState()
        vm.uiState.test {
            // Skip the Loading initialValue of stateIn; assert on the computed state.
            var state = awaitItem()
            while (state.listState is ListState.Loading) state = awaitItem()

            actions.forEach { vm.onAction(it) }
            vm.onAction(TransactionsAction.SelectScope(scope))
            while (!settled(state)) state = awaitItem()

            result = state
            cancelAndIgnoreRemainingEvents()
        }
        return result
    }

    private val TransactionsUiState.listed
        get() = (listState as? ListState.Content)
            ?.transactions
            ?.values
            ?.flatten()
            .orEmpty()

    @Test
    fun `the accounts scope closes with the payment and the adjustment in it`() = runTest(dispatcher) {
        val overview = stateUnder(TransactionScope.ACCOUNTS).balanceOverview as BalanceOverview.Accounts

        // Opening: 500 last month. Flows: +300 salary, −60 groceries, −120 payment,
        // +25 adjustment. The transfer's two legs are both inside, so it is not a flow.
        assertEquals(500.0, overview.openingBalance)
        assertEquals(300.0, overview.income)
        assertEquals(60.0, overview.expense)
        assertEquals(120.0, overview.invoicePayment)
        assertEquals(25.0, overview.adjustment)
        assertEquals(645.0, overview.finalBalance)

        assertEquals(
            overview.finalBalance,
            overview.openingBalance + overview.income - overview.expense -
                overview.invoicePayment!! + overview.adjustment!!,
        )
    }

    @Test
    fun `the cards scope closes, in the ledger's sign, with the invoice adjustment in it`() = runTest(dispatcher) {
        val overview = stateUnder(TransactionScope.CARDS).balanceOverview as BalanceOverview.Cards

        // Owing 120 at the start. Flows: 90 spent takes it down, 120 paid brings it up,
        // 15 adjusted brings it up too, leaving 75 owed.
        assertEquals(-120.0, overview.openingBalance)
        assertEquals(90.0, overview.expense)
        assertEquals(120.0, overview.payment)
        assertEquals(15.0, overview.adjustment)
        assertEquals(-75.0, overview.finalBalance)

        assertEquals(
            overview.finalBalance,
            overview.openingBalance - overview.expense + overview.payment!! + overview.adjustment!!,
        )
    }

    @Test
    fun `the overall scope closes and aggregates spending from both books`() = runTest(dispatcher) {
        val overview = stateUnder(TransactionScope.ALL).balanceOverview as BalanceOverview.Overall

        // Opening net: 500 held − 120 owed = 380. Spending aggregates the account's 60
        // and the card's 90; the adjustments are +25 and +15 in natural sign.
        assertEquals(380.0, overview.openingNet)
        assertEquals(300.0, overview.income)
        assertEquals(150.0, overview.expense)
        assertEquals(40.0, overview.adjustment)
        assertEquals(570.0, overview.finalNet)

        assertEquals(
            overview.finalNet,
            overview.openingNet + overview.income - overview.expense + overview.adjustment!!,
        )
    }

    @Test
    fun `the invoice payment is informative and moves nothing in the overall scope`() = runTest(dispatcher) {
        val withPayment = stateUnder(TransactionScope.ALL).balanceOverview as BalanceOverview.Overall
        val withoutPayment = stateUnder(
            TransactionScope.ALL,
            transactions = everything - invoicePayment,
        ).balanceOverview as BalanceOverview.Overall

        // Built from a real payment, not from a hand-made pair of legs: both of its legs
        // are inside the perimeter, so removing it changes nothing but its own line.
        assertEquals(120.0, withPayment.invoicePayment, "it is shown")
        assertNull(withoutPayment.invoicePayment)
        assertEquals(withoutPayment.finalNet, withPayment.finalNet, "and it is not summed")
        assertEquals(withoutPayment.openingNet, withPayment.openingNet)
        assertEquals(withoutPayment.expense, withPayment.expense)
    }

    @Test
    fun `a transfer between accounts does not move the accounts scope`() = runTest(dispatcher) {
        val withTransfer = stateUnder(TransactionScope.ACCOUNTS).balanceOverview as BalanceOverview.Accounts
        val withoutTransfer = stateUnder(
            TransactionScope.ACCOUNTS,
            transactions = everything - transfer,
        ).balanceOverview as BalanceOverview.Accounts

        assertEquals(withoutTransfer.finalBalance, withTransfer.finalBalance)
        assertEquals(withoutTransfer.income, withTransfer.income)
        assertEquals(withoutTransfer.expense, withTransfer.expense)
    }

    @Test
    fun `each scope lists exactly the transactions with a leg in its perimeter`() = runTest(dispatcher) {
        val monthly = listOf(
            salary, groceriesExpense, transfer, cardPurchase,
            invoicePayment, accountAdjustment, invoiceAdjustment,
        )

        assertEquals(monthly.toSet(), stateUnder(TransactionScope.ALL).listed.toSet())

        assertEquals(
            setOf(salary, groceriesExpense, transfer, invoicePayment, accountAdjustment),
            stateUnder(TransactionScope.ACCOUNTS).listed.toSet(),
        )

        assertEquals(
            setOf(cardPurchase, invoicePayment, invoiceAdjustment),
            stateUnder(TransactionScope.CARDS).listed.toSet(),
        )
    }

    @Test
    fun `a list filter narrows the list and leaves every summary line alone`() = runTest(dispatcher) {
        val unfiltered = stateUnder(TransactionScope.ACCOUNTS)
        val filtered = stateUnder(
            TransactionScope.ACCOUNTS,
            actions = listOf(TransactionsAction.SelectCategory(groceries)),
            settled = { it.selectedScope == TransactionScope.ACCOUNTS && it.selectedCategory == groceries },
        )

        assertEquals(listOf(groceriesExpense), filtered.listed)
        assertEquals(unfiltered.balanceOverview, filtered.balanceOverview)
    }

    @Test
    fun `the cards scope cuts by the transaction's date, not by the invoice cycle`() = runTest(dispatcher) {
        // Bought last month, on an invoice that only falls due later: it belongs to the
        // month it was posted, exactly like the list beneath it.
        val overview = stateUnder(TransactionScope.CARDS).balanceOverview as BalanceOverview.Cards

        assertEquals(90.0, overview.expense, "only this month's purchase")
        assertEquals(-120.0, overview.openingBalance, "last month's purchase is the opening debt")
        assertEquals(
            setOf(cardPurchase, invoicePayment, invoiceAdjustment),
            stateUnder(TransactionScope.CARDS).listed.toSet(),
            "and last month's purchase is not in the list",
        )
    }

    @Test
    fun `the target filter is offered only in the overall scope`() = runTest(dispatcher) {
        assertEquals(true, stateUnder(TransactionScope.ALL).mustShowTargetFilter)
        assertEquals(false, stateUnder(TransactionScope.ACCOUNTS).mustShowTargetFilter)
        assertEquals(false, stateUnder(TransactionScope.CARDS).mustShowTargetFilter)
    }

    @Test
    fun `the instalment filter stops narrowing in the scope that stops offering it`() = runTest(dispatcher) {
        val onlyInstalments = listOf(TransactionsAction.ToggleInstallment(true))

        // In the overall scope it does narrow — nothing this month is an instalment —
        // so the assertion below is about the filter being dropped, not about it being
        // harmless to begin with.
        val narrowed = stateUnder(
            TransactionScope.ALL,
            actions = onlyInstalments,
            settled = { it.showInstallmentOnly },
        )
        assertEquals(emptyList(), narrowed.listed)

        val accounts = stateUnder(
            TransactionScope.ACCOUNTS,
            actions = onlyInstalments,
        )

        assertEquals(false, accounts.mustShowInstallmentFilter, "it is no longer offered")
        assertEquals(false, accounts.showInstallmentOnly)
        assertEquals(
            stateUnder(TransactionScope.ACCOUNTS).listed.toSet(),
            accounts.listed.toSet(),
            "and it no longer cuts: an invisible filter is indistinguishable from a short list",
        )
    }

    @Test
    fun `the target filter stops narrowing in the scope that stops offering it`() = runTest(dispatcher) {
        val cardsOnly = listOf(TransactionsAction.SelectTarget(TransactionTarget.CREDIT_CARD))

        val narrowed = stateUnder(
            TransactionScope.ALL,
            actions = cardsOnly,
            settled = { it.selectedTarget == TransactionTarget.CREDIT_CARD },
        )
        assertEquals(setOf(cardPurchase, invoicePayment, invoiceAdjustment), narrowed.listed.toSet())

        // Contradictory on its face — accounts under a card filter — which is why the
        // scope drops it instead of letting the two controls disagree.
        val accounts = stateUnder(TransactionScope.ACCOUNTS, actions = cardsOnly)

        assertNull(accounts.selectedTarget)
        assertEquals(stateUnder(TransactionScope.ACCOUNTS).listed.toSet(), accounts.listed.toSet())
    }
}
