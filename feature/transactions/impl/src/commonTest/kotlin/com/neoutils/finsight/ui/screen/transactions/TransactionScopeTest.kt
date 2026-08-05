@file:OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)

package com.neoutils.finsight.ui.screen.transactions

import app.cash.turbine.test
import com.neoutils.finsight.extension.DisplayAmount.SignPolicy
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

    private val List<Transaction>.ids get() = map { it.id }.toSet()

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

    // A dollar card paid from a real account: R$ 550 leave, US$ 100 of debt go, and the
    // conversion legs — outside every perimeter — are what balance each currency.
    private val cardUsd = Account(id = 201, name = "Card USD", type = AccountType.LIABILITY, currency = "USD")
    private val toConversionBrl = Account(id = 300, name = "CONVERSION", type = AccountType.CONVERSION, currency = "BRL")
    private val toConversionUsd = Account(id = 301, name = "CONVERSION", type = AccountType.CONVERSION, currency = "USD")

    private val crossInvoicePayment = op(
        21, date(18),
        listOf(
            entry(account, -550.0),
            entry(toConversionBrl, 550.0),
            entry(toConversionUsd, -100.0),
            entry(cardUsd, 100.0),
        ),
    )

    /** The two flow lines of whichever perimeter is in force; the cards scope has no income. */
    private val TransactionsUiState.flows
        get() = when (val overview = balanceOverview) {
            is BalanceOverview.Accounts -> overview.income to overview.expense
            is BalanceOverview.Cards -> null to overview.expense
            is BalanceOverview.Overall -> overview.income to overview.expense
            else -> null to null
        }

    private fun viewModel(transactions: List<Transaction> = everything) = TransactionsViewModel(
        filterLabel = null, category = null, filterTarget = null,
        transactionRepository = FakeTransactionRepository(transactions),
        categoryRepository = FakeCategoryRepository(),
        installmentRepository = NoInstallments,
        entryRepository = FakeLedger(transactions),
        consolidateMoney = consolidator(),
        observeConsolidationChanges = FakeLedger(transactions).consolidationChanges(),
            baseCurrencyRepository = FakeBaseCurrency(),
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

    /**
     * The ids the screen lists. The state carries display models, not the ledger
     * (`presentation-mapping`), so identity is the id — which is also all these tests ever
     * asked of the list.
     */
    private val TransactionsUiState.listed
        get() = (listState as? ListState.Content)
            ?.transactions
            ?.values
            ?.flatten()
            ?.map { it.id }
            .orEmpty()

    @Test
    fun `the accounts scope closes with the payment and the adjustment in it`() = runTest(dispatcher) {
        val overview = stateUnder(TransactionScope.ACCOUNTS).balanceOverview as BalanceOverview.Accounts

        // Opening: 500 last month. Flows: +300 salary, −60 groceries, −120 payment,
        // +25 adjustment. The transfer's two legs are both inside, so it is not a flow.
        // Each figure carries the sign it is displayed with, which is why the column
        // below is a plain sum: what the user reads is what adds up.
        assertEquals(500.0, overview.openingBalance.value)
        assertEquals(300.0, overview.income.value)
        assertEquals(-60.0, overview.expense.value)
        assertEquals(-120.0, overview.invoicePayment?.value)
        assertEquals(25.0, overview.adjustment?.value)
        assertEquals(645.0, overview.finalBalance.value)

        assertEquals(
            overview.finalBalance.value,
            overview.openingBalance.value + overview.income.value + overview.expense.value +
                overview.invoicePayment!!.value + overview.adjustment!!.value,
        )
    }

    @Test
    fun `the cards scope closes in the ledger's sign with the invoice adjustment in it`() = runTest(dispatcher) {
        val overview = stateUnder(TransactionScope.CARDS).balanceOverview as BalanceOverview.Cards

        // Owing 120 at the start. Flows: 90 spent takes it down, 120 paid brings it up,
        // 15 adjusted brings it up too, leaving 75 owed. The two ends are debt lines, so
        // they read as the magnitude owed — the flows between them stay in the ledger's
        // sign, which is what makes the column read like a statement.
        assertEquals(120.0, overview.openingBalance.value)
        assertEquals(-90.0, overview.expense.value)
        assertEquals(120.0, overview.payment?.value)
        assertEquals(15.0, overview.adjustment?.value)
        assertEquals(75.0, overview.finalBalance.value)

        assertEquals(
            -overview.finalBalance.value,
            -overview.openingBalance.value + overview.expense.value +
                overview.payment!!.value + overview.adjustment!!.value,
        )
    }

    @Test
    fun `the overall scope closes and aggregates spending from both books`() = runTest(dispatcher) {
        val overview = stateUnder(TransactionScope.ALL).balanceOverview as BalanceOverview.Overall

        // Opening net: 500 held − 120 owed = 380. Spending aggregates the account's 60
        // and the card's 90; the adjustments are +25 and +15 in natural sign.
        assertEquals(380.0, overview.openingNet.value)
        assertEquals(300.0, overview.income.value)
        assertEquals(-150.0, overview.expense.value)
        assertEquals(40.0, overview.adjustment?.value)
        assertEquals(570.0, overview.finalNet.value)

        assertEquals(
            overview.finalNet.value,
            overview.openingNet.value + overview.income.value + overview.expense.value +
                overview.adjustment!!.value,
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
        // Shown, but signless: both legs are inside this perimeter, so it moves nothing.
        assertEquals(120.0, withPayment.invoicePayment?.value, "it is shown")
        assertEquals(SignPolicy.NEUTRAL, withPayment.invoicePayment?.policy)
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

        assertEquals(monthly.ids, stateUnder(TransactionScope.ALL).listed.toSet())

        assertEquals(
            listOf(salary, groceriesExpense, transfer, invoicePayment, accountAdjustment).ids,
            stateUnder(TransactionScope.ACCOUNTS).listed.toSet(),
        )

        assertEquals(
            listOf(cardPurchase, invoicePayment, invoiceAdjustment).ids,
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

        assertEquals(listOf(groceriesExpense.id), filtered.listed)
        assertEquals(unfiltered.balanceOverview, filtered.balanceOverview)
    }

    @Test
    fun `the cards scope cuts by the transaction's date and not by the invoice cycle`() = runTest(dispatcher) {
        // Bought last month, on an invoice that only falls due later: it belongs to the
        // month it was posted, exactly like the list beneath it.
        val overview = stateUnder(TransactionScope.CARDS).balanceOverview as BalanceOverview.Cards

        assertEquals(-90.0, overview.expense.value, "only this month's purchase")
        assertEquals(120.0, overview.openingBalance.value, "last month's purchase is the opening debt")
        assertEquals(
            listOf(cardPurchase, invoicePayment, invoiceAdjustment).ids,
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
        assertEquals(listOf(cardPurchase, invoicePayment, invoiceAdjustment).ids, narrowed.listed.toSet())

        // Contradictory on its face — accounts under a card filter — which is why the
        // scope drops it instead of letting the two controls disagree.
        val accounts = stateUnder(TransactionScope.ACCOUNTS, actions = cardsOnly)

        assertNull(accounts.selectedTarget)
        assertEquals(stateUnder(TransactionScope.ACCOUNTS).listed.toSet(), accounts.listed.toSet())
    }

    /**
     * **A cross-currency transfer is internal too**, and it takes the per-currency read
     * to see it. Both monetary legs are inside the accounts perimeter; the conversion
     * legs post to system accounts, which are outside every perimeter — and being
     * outside must not turn an otherwise internal movement into a flow. What decides is
     * where the **monetary** legs are.
     *
     * The zero contribution is exact *per currency*, which is precisely what the spec
     * claims: consolidated at some later rate the same two legs would sum to the
     * exchange drift, not to zero.
     */
    @Test
    fun `a cross-currency transfer is internal to the accounts scope`() = runTest(dispatcher) {
        val dollars = Account(id = 3, name = "Chase", type = AccountType.ASSET, currency = "USD")
        val conversionBrl = Account(id = 300, name = "CONVERSION", type = AccountType.CONVERSION, currency = "BRL")
        val conversionUsd = Account(id = 301, name = "CONVERSION", type = AccountType.CONVERSION, currency = "USD")

        // R$ 550 leave, US$ 100 arrive, and each currency sums to zero on its own.
        val crossTransfer = op(
            20, date(16),
            listOf(
                entry(account, -550.0),
                entry(conversionBrl, 550.0),
                entry(conversionUsd, -100.0),
                entry(dollars, 100.0),
            ),
        )

        val without = stateUnder(TransactionScope.ACCOUNTS)
        val with = stateUnder(TransactionScope.ACCOUNTS, transactions = everything + crossTransfer)

        val before = without.balanceOverview as BalanceOverview.Accounts
        val after = with.balanceOverview as BalanceOverview.Accounts

        // It is on the list...
        assertEquals(true, crossTransfer.id in with.listed)

        // ...and it moved no flow line. Income, expense and adjustment are untouched.
        assertEquals(before.income.value, after.income.value)
        assertEquals(before.expense.value, after.expense.value)
        assertEquals(before.adjustment?.value, after.adjustment?.value)
    }

    /**
     * **The cross-currency invoice payment, in all three perimeters** — the case that
     * exercises what the transfer cannot: one monetary leg outside the accounts perimeter
     * (the card's) *and* conversion legs outside every one of them.
     *
     * What each scope must not do is let the conversion legs, which are outside by
     * definition, read as a flow of the perimeter they are outside of. Paying a dollar
     * invoice with reais moves money, but it earns nothing and spends nothing — in any
     * of the three.
     */
    @Test
    fun `a cross-currency invoice payment is a flow in no scope`() = runTest(dispatcher) {
        val paid = everything + crossInvoicePayment

        for (scope in listOf(TransactionScope.ALL, TransactionScope.ACCOUNTS, TransactionScope.CARDS)) {
            val before = stateUnder(scope)
            val after = stateUnder(scope, transactions = paid)

            assertEquals(true, crossInvoicePayment.id in after.listed, "listed under $scope")

            val (beforeIncome, beforeExpense) = before.flows
            val (afterIncome, afterExpense) = after.flows

            assertEquals(beforeIncome, afterIncome, "income moved under $scope")
            assertEquals(beforeExpense, afterExpense, "expense moved under $scope")
        }
    }

    /**
     * And what it *does* move is the two monetary legs, each in its own currency and
     * neither netted against the other: reais down by 550, dollars owed down by 100. The
     * conversion legs — the +550 and the −100 that balance the transaction — contribute
     * nothing, which is what "outside every perimeter" means when stated as a number.
     *
     * A monomoeda payment nets to zero here (`the invoice payment is informative`); this
     * one does not, and that is not an exception to the perimeter rule. Σ = 0 holds *per
     * currency*, and the halves that cancel are one leg inside and one leg outside.
     */
    @Test
    fun `a cross-currency invoice payment moves each currency on its own`() = runTest(dispatcher) {
        val before = stateUnder(TransactionScope.ALL).balanceOverview as BalanceOverview.Overall
        val after = stateUnder(TransactionScope.ALL, transactions = everything + crossInvoicePayment)
            .balanceOverview as BalanceOverview.Overall

        val moved = after.finalNet.terms.associate { it.currency to it.value } -
            before.finalNet.terms.associate { it.currency to it.value }.keys

        assertEquals(mapOf("USD" to 100.0), moved, "the dollar debt is the only new term")

        val brlBefore = before.finalNet.terms.single { it.currency == "BRL" }.value
        val brlAfter = after.finalNet.terms.single { it.currency == "BRL" }.value

        assertEquals(-550.0, brlAfter - brlBefore, "the reais that left, and nothing else")
    }

    /**
     * And what the closing balance gains is the two legs *as they are*: reais down by
     * 550, dollars up by 100, side by side. Nothing added them, because with no rate in
     * the archive there is nothing to add them with — and even with one, adding them is
     * the reducer's decision and not the ledger's.
     */
    @Test
    fun `the closing balance of a cross-currency month keeps a term per currency`() = runTest(dispatcher) {
        val dollars = Account(id = 3, name = "Chase", type = AccountType.ASSET, currency = "USD")
        val conversionBrl = Account(id = 300, name = "CONVERSION", type = AccountType.CONVERSION, currency = "BRL")
        val conversionUsd = Account(id = 301, name = "CONVERSION", type = AccountType.CONVERSION, currency = "USD")

        val crossTransfer = op(
            20, date(16),
            listOf(
                entry(account, -550.0),
                entry(conversionBrl, 550.0),
                entry(conversionUsd, -100.0),
                entry(dollars, 100.0),
            ),
        )

        val overview = stateUnder(TransactionScope.ACCOUNTS, transactions = everything + crossTransfer)
            .balanceOverview as BalanceOverview.Accounts

        val terms = overview.finalBalance.terms.associate { it.currency to it.value }

        assertEquals(mapOf("BRL" to 95.0, "USD" to 100.0), terms)
        assertEquals(true, overview.finalBalance.isApproximate, "two currencies went in")
    }
}
