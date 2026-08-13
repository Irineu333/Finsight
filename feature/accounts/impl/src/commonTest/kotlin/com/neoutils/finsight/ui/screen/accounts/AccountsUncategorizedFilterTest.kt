@file:OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)

package com.neoutils.finsight.ui.screen.accounts

import app.cash.turbine.test
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.model.SpendingSubject
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.extension.toYearMonth
import com.neoutils.finsight.ui.icons.CategoryLazyIcon
import com.neoutils.finsight.ui.screen.accounts.AccountsUiState.ListState
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
 * The unclassified cut on the accounts screen, which filters **display models** rather than
 * ledger transactions.
 *
 * That is the whole reason these tests exist separately: here `categoryId` is null for a
 * loose expense, for a transfer and for an orphan dimension alike, so the cut can only be
 * right if it reads the answer the mapper carried across.
 */
class AccountsUncategorizedFilterTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setup() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private val month = Clock.System.now().toYearMonth()

    private val wallet = Account(id = 1, name = "Wallet", type = AccountType.ASSET, isDefault = true, currency = "BRL")
    private val savings = Account(id = 2, name = "Savings", type = AccountType.ASSET, currency = "BRL")
    private val card = Account(id = 3, name = "Card", type = AccountType.LIABILITY, currency = "BRL")
    private val expenseAcc = Account(id = 99, name = "expense", type = AccountType.EXPENSE, currency = "BRL")
    private val equityAcc = Account(id = 98, name = "reconciliation", type = AccountType.EQUITY, currency = "BRL")

    private val food = Category(
        id = 1, name = "Food", icon = CategoryLazyIcon("food"),
        type = Category.Type.EXPENSE, createdAt = 1, dimensionId = 7,
    )

    private fun date(day: Int) = LocalDate(month.year, month.month, day)

    private fun entry(account: Account, amount: Long, dimensionId: Long? = null) =
        Entry(account = account, amount = amount, dimensionId = dimensionId)

    private fun op(id: Long, day: Int, vararg entries: Entry) =
        Transaction(id = id, title = "op$id", date = date(day), entries = entries.toList())

    private val looseExpense = op(1, 2, entry(wallet, -1_000), entry(expenseAcc, 1_000))
    private val classifiedExpense = op(2, 3, entry(wallet, -2_000), entry(expenseAcc, 2_000, dimensionId = food.dimensionId))
    private val transfer = op(3, 4, entry(wallet, -3_000), entry(savings, 3_000))
    private val invoicePayment = op(4, 5, entry(wallet, -4_000), entry(card, 4_000))
    private val adjustment = op(5, 6, entry(wallet, 500), entry(equityAcc, -500))
    private val orphanExpense = op(6, 7, entry(wallet, -600), entry(expenseAcc, 600, dimensionId = 999))

    private val everything = listOf(
        looseExpense, classifiedExpense, transfer, invoicePayment, adjustment, orphanExpense,
    )

    private fun viewModel(transactions: List<Transaction> = everything) = AccountsViewModel(
        accountRepository = FakeAccountRepository(wallet),
        transactionRepository = FakeTransactionRepository(transactions),
        categoryRepository = FakeCategoryRepository(listOf(food)),
        installmentRepository = NoInstallments,
        entryRepository = FlatEntryRepository,
        clock = Clock.System,
    )

    private val AccountsUiState.Content.listed
        get() = (listState as? ListState.Content)
            ?.transactions
            ?.values
            ?.flatten()
            ?.map { it.id }
            ?.toSet()
            .orEmpty()

    @Test
    fun `the cut holds only what has a nominal leg carrying no dimension`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.uiState.test {
            awaitContent()
            vm.onAction(AccountsAction.SelectSubject(SpendingSubject.Uncategorized))

            var content = awaitContent()
            while (content.selectedSubject != SpendingSubject.Uncategorized) content = awaitContent()

            assertEquals(setOf(looseExpense.id), content.listed)
        }
    }

    @Test
    fun `a category still cuts to its own`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.uiState.test {
            awaitContent()
            vm.onAction(AccountsAction.SelectSubject(SpendingSubject.Categorized(food)))

            var content = awaitContent()
            while (content.selectedSubject != SpendingSubject.Categorized(food)) content = awaitContent()

            assertEquals(setOf(classifiedExpense.id), content.listed)
        }
    }

    @Test
    fun `a month with nothing unclassified offers to clear the filters`() = runTest(dispatcher) {
        val vm = viewModel(listOf(classifiedExpense, transfer))

        vm.uiState.test {
            assertIs<ListState.Content>(awaitContent().listState)
            vm.onAction(AccountsAction.SelectSubject(SpendingSubject.Uncategorized))

            var content = awaitContent()
            while (content.selectedSubject != SpendingSubject.Uncategorized) content = awaitContent()

            val listState = assertIs<ListState.EmptyScope>(content.listState)
            assertEquals(true, listState.canClearFilters)
        }
    }

    @Test
    fun `the value is offered only when the cut has something to find`() = runTest(dispatcher) {
        viewModel().uiState.test {
            assertEquals(true, awaitContent().mustShowUncategorizedFilter)
        }

        viewModel(listOf(classifiedExpense, transfer, invoicePayment, adjustment, orphanExpense))
            .uiState.test {
                assertEquals(
                    false,
                    awaitContent().mustShowUncategorizedFilter,
                    "nothing here is unclassified — the others are outside the axis, and the " +
                        "orphan is an integrity failure",
                )
            }
    }

    @Test
    fun `the value stays offered while it is the active cut`() = runTest(dispatcher) {
        val vm = viewModel(listOf(classifiedExpense))

        vm.uiState.test {
            awaitContent()
            vm.onAction(AccountsAction.SelectSubject(SpendingSubject.Uncategorized))

            var content = awaitContent()
            while (content.selectedSubject == null) content = awaitContent()

            assertEquals(false, content.hasUncategorized)
            assertEquals(true, content.mustShowUncategorizedFilter)
        }
    }

    @Test
    fun `clearing returns the axis to neutral`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.uiState.test {
            awaitContent()
            vm.onAction(AccountsAction.SelectSubject(SpendingSubject.Uncategorized))

            var content = awaitContent()
            while (content.selectedSubject == null) content = awaitContent()

            vm.onAction(AccountsAction.ClearFilters)

            var cleared = awaitContent()
            while (cleared.selectedSubject != null) cleared = awaitContent()

            assertEquals(everything.map { it.id }.toSet(), cleared.listed)
        }
    }
}
