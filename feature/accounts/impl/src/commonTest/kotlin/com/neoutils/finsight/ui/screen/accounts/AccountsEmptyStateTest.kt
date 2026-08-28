@file:OptIn(ExperimentalCoroutinesApi::class)

package com.neoutils.finsight.ui.screen.accounts

import app.cash.turbine.test
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.SpendingSubject
import com.neoutils.finsight.domain.model.ContraLeg
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.model.Installment
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionIntent
import com.neoutils.finsight.domain.model.TransactionLeg
import com.neoutils.finsight.domain.repository.AccountFlows
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.repository.IInstallmentRepository
import com.neoutils.finsight.domain.repository.ITransactionRepository
import com.neoutils.finsight.extension.toYearMonth
import com.neoutils.finsight.ui.icons.CategoryLazyIcon
import com.neoutils.finsight.ui.screen.accounts.AccountsUiState.ListState
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
import kotlinx.datetime.minusMonth
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import com.neoutils.finsight.domain.model.MoneyByCurrency
import com.neoutils.finsight.domain.repository.AssetMonthFlowsByCurrency
import com.neoutils.finsight.domain.repository.DimensionFlowsByCurrency
import com.neoutils.finsight.domain.repository.LiabilityMonthFlowsByCurrency
import com.neoutils.finsight.domain.repository.ScopeStatsByCurrency

/**
 * The two emptinesses of the accounts list, and the one way out of the second.
 */
@OptIn(ExperimentalTime::class)
class AccountsEmptyStateTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setup() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private val currentMonth = Clock.System.now().toYearMonth()

    private val account = Account(id = 1, name = "Wallet", type = AccountType.ASSET, isDefault = true, currency = "BRL")
    private val expenseAccount = Account(id = 99, name = "Expense", type = AccountType.EXPENSE, currency = "BRL")

    private val food = Category(
        id = 1,
        name = "Food",
        icon = CategoryLazyIcon("food"),
        type = Category.Type.EXPENSE,
        createdAt = 1,
        dimensionId = 7,
    )

    private fun expense(id: Long, month: YearMonth, dimensionId: Long? = null) = Transaction(
        id = id,
        title = "Lunch",
        date = LocalDate(month.year, month.month, 10),
        entries = listOf(
            Entry(transactionId = id, account = account, amount = -1_000),
            Entry(transactionId = id, account = expenseAccount, amount = 1_000, dimensionId = dimensionId),
        ),
    )

    private fun viewModel(transactions: List<Transaction>) = AccountsViewModel(
        accountRepository = FakeAccountRepository(account),
        transactionRepository = FakeTransactionRepository(transactions),
        categoryRepository = FakeCategoryRepository(listOf(food)),
        installmentRepository = NoInstallments,
        entryRepository = FlatEntryRepository,
        clock = Clock.System,
    )

    @Test
    fun `an account that never moved reads as an empty account`() = runTest(dispatcher) {
        viewModel(transactions = emptyList()).uiState.test {
            val content = awaitContent()
            assertEquals(ListState.EmptyAccount, content.listState)
        }
    }

    @Test
    fun `a month without entries in an account that moves reads as an empty cut`() = runTest(dispatcher) {
        val vm = viewModel(listOf(expense(id = 1, month = currentMonth.minusMonth())))

        vm.uiState.test {
            val content = awaitContent()
            val listState = assertIs<ListState.EmptyScope>(content.listState)
            assertEquals(
                expected = false,
                actual = listState.canClearFilters,
                message = "no filter is narrowing, so there is nothing to clear",
            )
        }
    }

    @Test
    fun `a filter that cuts everything offers to clear`() = runTest(dispatcher) {
        val vm = viewModel(listOf(expense(id = 1, month = currentMonth)))

        vm.uiState.test {
            assertIs<ListState.Content>(awaitContent().listState)

            vm.onAction(AccountsAction.SelectSubject(SpendingSubject.Categorized(food)))

            val listState = assertIs<ListState.EmptyScope>(awaitContent().listState)
            assertEquals(true, listState.canClearFilters)
        }
    }

    @Test
    fun `clearing the filters keeps the month and the account`() = runTest(dispatcher) {
        val previousMonth = currentMonth.minusMonth()
        val vm = viewModel(listOf(expense(id = 1, month = previousMonth, dimensionId = null)))

        vm.uiState.test {
            awaitContent()

            vm.onAction(AccountsAction.SelectMonth(previousMonth))
            vm.onAction(AccountsAction.SelectSubject(SpendingSubject.Categorized(food)))

            var content = awaitContent()
            while (content.selectedSubject == null || content.selectedMonth != previousMonth) {
                content = awaitContent()
            }
            assertIs<ListState.EmptyScope>(content.listState)

            vm.onAction(AccountsAction.ClearFilters)

            var cleared = awaitContent()
            while (cleared.selectedSubject != null) cleared = awaitContent()
            assertEquals(previousMonth, cleared.selectedMonth, "the month is not a filter")
            assertEquals(1L, cleared.selectedAccountId, "the account is not a filter either")
            assertEquals(false, cleared.showRecurringOnly)
            assertIs<ListState.Content>(cleared.listState)
        }
    }
}

internal suspend fun app.cash.turbine.TurbineTestContext<AccountsUiState>.awaitContent(): AccountsUiState.Content {
    var state = awaitItem()
    while (state !is AccountsUiState.Content) state = awaitItem()
    return state
}

internal class FakeAccountRepository(private val account: Account) : IAccountRepository {
    override fun observeAllAccounts(): Flow<List<Account>> = MutableStateFlow(listOf(account))
    override fun observeAllAccountsIncludingClosed(): Flow<List<Account>> = observeAllAccounts()
    override suspend fun getAllAccounts(): List<Account> = listOf(account)
    override suspend fun getAllAccountsIncludingClosed(): List<Account> = listOf(account)
    override suspend fun getAllLedgerAccounts(): List<Account> = listOf(account)
    override fun observeAllLedgerAccounts(): Flow<List<Account>> = observeAllAccounts()
    override suspend fun getAccountById(accountId: Long): Account? = account
    override fun observeAccountById(accountId: Long): Flow<Account?> = MutableStateFlow(account)
    override suspend fun getDefaultAccount(): Account = account
    override fun observeDefaultAccount(): Flow<Account?> = MutableStateFlow(account)
    override suspend fun hasYieldingAccount(): Boolean = false
    override suspend fun getAccountCount(): Int = 1
    override suspend fun insert(account: Account): Long = throw NotImplementedError()
    override suspend fun update(account: Account) = throw NotImplementedError()
    override suspend fun delete(account: Account) = throw NotImplementedError()
    override suspend fun reopen(accountId: Long) = throw NotImplementedError()
}

internal class FakeTransactionRepository(private val transactions: List<Transaction>) : ITransactionRepository {
    override fun observeAllTransactions(): Flow<List<Transaction>> = MutableStateFlow(transactions)
    override fun observeTransactionsBy(date: LocalDate?, dimensionId: Long?, accountId: Long?): Flow<List<Transaction>> =
        MutableStateFlow(transactions)

    override fun observeTransactionById(id: Long): Flow<Transaction?> = throw NotImplementedError()
    override suspend fun getAllTransactions(): List<Transaction> = transactions
    override suspend fun getTransactionsByIds(ids: Collection<Long>): List<Transaction> =
        transactions.filter { it.id in ids }

    override suspend fun getTransactionById(id: Long): Transaction? = transactions.firstOrNull { it.id == id }
    override suspend fun createTransaction(intent: TransactionIntent): Transaction = throw NotImplementedError()
    override suspend fun createTransactions(intents: List<TransactionIntent>): List<Transaction> = throw NotImplementedError()
    override suspend fun updateTransaction(id: Long, title: String?, date: LocalDate, legs: List<TransactionLeg>, contra: ContraLeg?) =
        throw NotImplementedError()

    override suspend fun deleteTransactionsByIds(ids: List<Long>) = throw NotImplementedError()
    override suspend fun deleteTransactionById(id: Long) = throw NotImplementedError()
}

internal class FakeCategoryRepository(private val categories: List<Category> = emptyList()) : ICategoryRepository {
    override fun observeAllCategories(): Flow<List<Category>> = MutableStateFlow(categories)
    override fun observeAllCategoriesIncludingClosed(): Flow<List<Category>> = observeAllCategories()
    override fun observeCategoryById(id: Long): Flow<Category?> = throw NotImplementedError()
    override suspend fun getAllCategories(): List<Category> = categories
    override suspend fun getAllCategoriesIncludingClosed(): List<Category> = categories
    override fun observeCategoriesByType(type: Category.Type): Flow<List<Category>> = throw NotImplementedError()
    override suspend fun getCategoryById(id: Long): Category? = categories.firstOrNull { it.id == id }
    override suspend fun getCategoryBySystemKey(systemKey: String): Category? = null
    override suspend fun getCategoryByDimensionId(dimensionId: Long): Category? =
        categories.firstOrNull { it.dimensionId == dimensionId }

    override suspend fun archive(id: Long) = Unit
    override suspend fun unarchive(id: Long) = Unit
    override suspend fun existsByName(name: String, ignoreId: Long): Boolean = false
    override suspend fun insert(category: Category) = throw NotImplementedError()
    override suspend fun insertAll(categories: List<Category>) = throw NotImplementedError()
    override suspend fun update(category: Category) = throw NotImplementedError()
    override suspend fun delete(category: Category) = throw NotImplementedError()
}

internal object NoInstallments : IInstallmentRepository {
    override fun observeAllInstallments(): Flow<List<Installment>> = flowOf(emptyList())
    override suspend fun getAllInstallments(): List<Installment> = emptyList()
    override suspend fun getInstallmentById(id: Long): Installment? = null
    override suspend fun createInstallment(count: Int, totalAmount: Double): Long = throw NotImplementedError()
    override suspend fun updateInstallment(id: Long, count: Int, totalAmount: Double) = throw NotImplementedError()
    override suspend fun deleteInstallmentById(id: Long) = throw NotImplementedError()
}

/** No figure is under test here; the card at the top only needs the reads to answer. */
internal object FlatEntryRepository : IEntryRepository {
    override suspend fun getEntriesByTransaction(transactionId: Long): List<Entry> = emptyList()
    override fun observeEntriesByTransaction(transactionId: Long): Flow<List<Entry>> = flowOf(emptyList())
    override fun observeLedgerChanges(): Flow<Unit> = flowOf(Unit)
    override suspend fun accountBalanceUpTo(accountId: Long, target: LocalDate): Double = 0.0
    override suspend fun balance(accountId: Long): Double = 0.0
    override suspend fun hasEntries(accountId: Long): Boolean = false
    override suspend fun hasEntriesForDimension(dimensionId: Long): Boolean = false
    override suspend fun accountFlows(month: YearMonth, accountId: Long, yieldDimensionId: Long?) = AccountFlows("BRL", 0.0, 0.0, 0.0, 0.0, 0.0)

    override suspend fun balanceUpToByCurrency(target: YearMonth, excludedAccountIds: Set<Long>): MoneyByCurrency = throw NotImplementedError()
    override suspend fun naturalBalanceUpToByCurrency(target: YearMonth, type: AccountType, excludedAccountIds: Set<Long>): MoneyByCurrency = throw NotImplementedError()
    override suspend fun dimensionMonthlySeriesByCurrency(dimensionId: Long, upTo: YearMonth): Map<YearMonth, MoneyByCurrency> = throw NotImplementedError()
    override suspend fun dimensionBalanceInMonthByCurrency(month: YearMonth, dimensionId: Long): MoneyByCurrency = throw NotImplementedError()
    override suspend fun dimensionOwedByCurrency(dimensionId: Long): MoneyByCurrency = throw NotImplementedError()
    override suspend fun dimensionFlowsByCurrency(dimensionId: Long): DimensionFlowsByCurrency = throw NotImplementedError()
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
