@file:OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)

package com.neoutils.finsight.ui.screen.transactions

import app.cash.turbine.test
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.model.SystemCategoryKey
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.extension.toYearMonth
import com.neoutils.finsight.ui.icons.CategoryLazyIcon
import com.neoutils.finsight.ui.screen.transactions.TransactionsUiState.BalanceOverview
import com.neoutils.finsight.ui.screen.transactions.TransactionsUiState.ListState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
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
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * The yield line is a **repartition**, so the only thing worth pinning is that the
 * column still closes: what the yield line shows, the income line stopped showing,
 * and the two together are what income alone was.
 */
class TransactionScopeYieldTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setup() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private val month = Clock.System.now().toYearMonth()
    private val previous = month.minusMonth()

    private val account = Account(id = 1, name = "Nubank", type = AccountType.ASSET, yieldsInterest = true)
    private val cardAcc = Account(id = 200, name = "Card", type = AccountType.LIABILITY)
    private val incomeAcc = Account(id = 100, name = "income", type = AccountType.INCOME)
    private val expenseAcc = Account(id = 101, name = "expense", type = AccountType.EXPENSE)

    private val yieldCategory = Category(
        id = 9, name = "Rendimentos", icon = CategoryLazyIcon("savings"),
        type = Category.Type.INCOME, createdAt = 0L, dimensionId = 90,
        systemKey = SystemCategoryKey.YIELD,
    )

    private fun date(day: Int) = LocalDate(month.year, month.month, day)
    private fun entry(acc: Account, amount: Double, dimensionId: Long? = null) =
        Entry(account = acc, amount = (amount * 100).toLong(), dimensionId = dimensionId)
    private fun op(id: Long, date: LocalDate, entries: List<Entry>) =
        Transaction(id = id, title = null, date = date, entries = entries)

    private val salary = op(10, date(2), listOf(entry(account, 5_000.0), entry(incomeAcc, -5_000.0)))
    private val yieldOp = op(
        11, date(2),
        listOf(entry(account, 12.40), entry(incomeAcc, -12.40, dimensionId = yieldCategory.dimensionId)),
    )
    private val secondYieldOp = op(
        12, date(28),
        listOf(entry(account, 8.0), entry(incomeAcc, -8.0, dimensionId = yieldCategory.dimensionId)),
    )
    private val groceries = op(13, date(4), listOf(entry(account, -60.0), entry(expenseAcc, 60.0)))
    private val cardPurchase = op(14, date(8), listOf(entry(cardAcc, -90.0), entry(expenseAcc, 90.0)))

    private val everything = listOf(salary, yieldOp, secondYieldOp, groceries, cardPurchase)

    private fun viewModel(
        transactions: List<Transaction>,
        yieldCategoryExists: Boolean = true,
    ) = TransactionsViewModel(
        filterLabel = null, category = null, filterTarget = null,
        transactionRepository = FakeTransactionRepository(transactions),
        categoryRepository = YieldAwareCategories(yieldCategory.takeIf { yieldCategoryExists }),
        installmentRepository = NoInstallments,
        entryRepository = FakeLedger(transactions),
        clock = Clock.System,
    )

    private suspend fun overviewUnder(
        scope: TransactionScope,
        transactions: List<Transaction> = everything,
        yieldCategoryExists: Boolean = true,
    ): BalanceOverview {
        val vm = viewModel(transactions, yieldCategoryExists)
        var result: BalanceOverview = BalanceOverview.Overall()
        vm.uiState.test {
            var state = awaitItem()
            while (state.listState is ListState.Loading) state = awaitItem()
            vm.onAction(TransactionsAction.SelectScope(scope))
            while (state.selectedScope != scope) state = awaitItem()
            result = state.balanceOverview
            cancelAndIgnoreRemainingEvents()
        }
        return result
    }

    @Test
    fun `the accounts scope closes with the yield line in it`() = runTest(dispatcher) {
        val overview = assertIs<BalanceOverview.Accounts>(overviewUnder(TransactionScope.ACCOUNTS))

        assertEquals(5_000.0, overview.income.value)
        assertEquals(20.40, overview.yield?.value)
        assertEquals(-60.0, overview.expense.value)
        assertEquals(4_960.40, overview.finalBalance.value)

        assertEquals(
            overview.finalBalance.value,
            overview.openingBalance.value + overview.income.value + overview.yield!!.value +
                overview.expense.value,
        )
    }

    @Test
    fun `the yield line takes exactly what the income line gives up`() = runTest(dispatcher) {
        val segregated = assertIs<BalanceOverview.Accounts>(overviewUnder(TransactionScope.ACCOUNTS))
        val undivided = assertIs<BalanceOverview.Accounts>(
            overviewUnder(TransactionScope.ACCOUNTS, yieldCategoryExists = false)
        )

        assertNull(undivided.yield, "nothing separates it, so there is no line")
        assertEquals(5_020.40, undivided.income.value)
        assertEquals(undivided.income.value, segregated.income.value + segregated.yield!!.value)
        assertEquals(undivided.finalBalance.value, segregated.finalBalance.value)
    }

    @Test
    fun `a month without yield has no yield line`() = runTest(dispatcher) {
        val overview = assertIs<BalanceOverview.Accounts>(
            overviewUnder(TransactionScope.ACCOUNTS, transactions = listOf(salary, groceries))
        )

        // Nothing to launch from here, so a line at zero would say nothing. The account
        // card is where a declared account is offered its empty line.
        assertNull(overview.yield)
    }

    @Test
    fun `a yield already recorded keeps its line however the account is configured now`() = runTest(dispatcher) {
        // The account has stopped declaring a yield, but the month still holds one and
        // `income` no longer contains it: hiding the line would break the column.
        val retired = account.copy(yieldsInterest = false)
        val overview = assertIs<BalanceOverview.Accounts>(
            overviewUnder(
                TransactionScope.ACCOUNTS,
                transactions = everything.map { op ->
                    op.copy(entries = op.entries.map { entry ->
                        if (entry.account.id == account.id) entry.copy(account = retired) else entry
                    })
                },
            )
        )

        assertEquals(20.40, overview.yield?.value)
        assertEquals(
            overview.finalBalance.value,
            overview.openingBalance.value + overview.income.value + overview.yield!!.value +
                overview.expense.value,
        )
    }

    @Test
    fun `the overall scope closes with the yield line in it`() = runTest(dispatcher) {
        val overview = assertIs<BalanceOverview.Overall>(overviewUnder(TransactionScope.ALL))

        assertEquals(5_000.0, overview.income.value)
        assertEquals(20.40, overview.yield?.value)
        assertEquals(-150.0, overview.expense.value)

        assertEquals(
            overview.finalNet.value,
            overview.openingNet.value + overview.income.value + overview.yield!!.value +
                overview.expense.value,
        )
    }

    @Test
    fun `the cards scope has no yield line at all`() = runTest(dispatcher) {
        // Structural, not conditional: the LIABILITY perimeter has nothing to
        // segregate, so its overview does not carry the figure (design D7).
        assertIs<BalanceOverview.Cards>(overviewUnder(TransactionScope.CARDS))
    }
}

private class YieldAwareCategories(private val yieldCategory: Category?) : ICategoryRepository {
    override suspend fun getCategoryBySystemKey(systemKey: String): Category? =
        yieldCategory?.takeIf { it.systemKey == systemKey }
    override fun observeAllCategories(): Flow<List<Category>> = flowOf(listOfNotNull(yieldCategory))
    override suspend fun getAllCategories(): List<Category> = listOfNotNull(yieldCategory)
    override suspend fun getAllCategoriesIncludingClosed(): List<Category> = listOfNotNull(yieldCategory)
    override fun observeAllCategoriesIncludingClosed(): Flow<List<Category>> = flowOf(listOfNotNull(yieldCategory))
    override fun observeCategoriesByType(type: Category.Type): Flow<List<Category>> = flowOf(emptyList())
    override suspend fun getCategoryById(id: Long): Category? = yieldCategory?.takeIf { it.id == id }
    override suspend fun getCategoryByDimensionId(dimensionId: Long): Category? =
        yieldCategory?.takeIf { it.dimensionId == dimensionId }
    override fun observeCategoryById(id: Long): Flow<Category?> = flowOf(null)
    override suspend fun archive(id: Long) = throw NotImplementedError()
    override suspend fun unarchive(id: Long) = throw NotImplementedError()
    override suspend fun existsByName(name: String, ignoreId: Long): Boolean = false
    override suspend fun insert(category: Category) = throw NotImplementedError()
    override suspend fun insertAll(categories: List<Category>) = throw NotImplementedError()
    override suspend fun update(category: Category) = throw NotImplementedError()
    override suspend fun delete(category: Category) = throw NotImplementedError()
}
