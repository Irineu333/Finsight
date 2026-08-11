package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.error.RetireError
import com.neoutils.finsight.domain.exception.RetireException
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.Budget
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.repository.AccountFlows
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.IBudgetRepository
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.repository.IRecurringRepository
import com.neoutils.finsight.ui.icons.CategoryLazyIcon
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import com.neoutils.finsight.domain.model.MoneyByCurrency
import com.neoutils.finsight.domain.repository.AssetMonthFlowsByCurrency
import com.neoutils.finsight.domain.repository.DimensionFlowsByCurrency
import com.neoutils.finsight.domain.repository.LiabilityMonthFlowsByCurrency
import com.neoutils.finsight.domain.repository.ScopeStatsByCurrency

/**
 * A category with no movement is deletable — unless a budget or a recurring still
 * points at it, exactly the guards account and card carry. `budget_categories` is
 * CASCADE and `recurring.categoryId` is SET_NULL, so without these the reference
 * would be stripped silently rather than refused.
 */
class DeleteCategoryGuardsTest {

    private val category = Category(
        id = 1, name = "Food", icon = CategoryLazyIcon("food"),
        type = Category.Type.EXPENSE, createdAt = 0L, dimensionId = 10,
    )

    private fun useCase(
        hasEntries: Boolean = false,
        hasBudget: Boolean = false,
        hasRecurring: Boolean = false,
        hasYieldingAccount: Boolean = false,
        repo: RecordingCategoryRepository = RecordingCategoryRepository(),
    ) = DeleteCategoryUseCase(
        categoryRepository = repo,
        resolveRetirability = ResolveCategoryRetirabilityUseCase(
            entryRepository = FakeEntries(hasEntries),
            budgetRepository = FakeBudget(hasBudget),
            recurringRepository = FakeRecurring(hasRecurring),
            accountRepository = FakeAccounts(hasYieldingAccount),
        ),
    )

    @Test
    fun `an unused category with no dependents is deleted`() = runTest {
        val repo = RecordingCategoryRepository()
        assertTrue(useCase(repo = repo)(category).isRight())
        assertEquals(listOf(category.id), repo.deleted)
    }

    @Test
    fun `a category with movement is refused`() = runTest {
        val repo = RecordingCategoryRepository()
        val error = assertIs<RetireException>(useCase(hasEntries = true, repo = repo)(category).leftOrNull())
        assertEquals(RetireError.HAS_TRANSACTIONS, error.error)
        assertTrue(repo.deleted.isEmpty())
        // Delete refused MUST error, never silently fall back to archiving.
        assertTrue(repo.archived.isEmpty(), "a refused delete must not archive")
    }

    @Test
    fun `a category still in a budget is refused`() = runTest {
        // budget_categories is CASCADE: deleting would strip it from the budget.
        val repo = RecordingCategoryRepository()
        val error = assertIs<RetireException>(useCase(hasBudget = true, repo = repo)(category).leftOrNull())
        assertEquals(RetireError.HAS_BUDGET, error.error)
        assertTrue(repo.deleted.isEmpty(), "nothing may be removed")
        assertTrue(repo.archived.isEmpty(), "a refused delete must not archive")
    }

    @Test
    fun `a category a recurring still points at is refused`() = runTest {
        // recurring.categoryId is SET_NULL: the template would survive uncategorized.
        val repo = RecordingCategoryRepository()
        val error = assertIs<RetireException>(useCase(hasRecurring = true, repo = repo)(category).leftOrNull())
        assertEquals(RetireError.HAS_RECURRING, error.error)
        assertTrue(repo.deleted.isEmpty())
        assertTrue(repo.archived.isEmpty(), "a refused delete must not archive")
    }
}

// Shared across the domain use-case tests in this package: records the retire calls
// and answers existsByName from a seeded name list.
class RecordingCategoryRepository(
    private val existing: List<Category> = emptyList(),
) : ICategoryRepository {
    val deleted = mutableListOf<Long>()
    val archived = mutableListOf<Long>()
    val unarchived = mutableListOf<Long>()
    val insertedBatches = mutableListOf<List<Category>>()
    override suspend fun delete(category: Category) { deleted += category.id }
    override suspend fun unarchive(id: Long) { unarchived += id }
    override suspend fun existsByName(name: String, ignoreId: Long): Boolean =
        existing.any { it.name.equals(name, ignoreCase = true) && it.id != ignoreId }
    override fun observeAllCategories(): Flow<List<Category>> = flowOf(emptyList())
    override suspend fun getAllCategories(): List<Category> = emptyList()
    override suspend fun getAllCategoriesIncludingClosed(): List<Category> = existing
    override fun observeAllCategoriesIncludingClosed(): Flow<List<Category>> = flowOf(existing)
    override fun observeCategoriesByType(type: Category.Type): Flow<List<Category>> = flowOf(emptyList())
    override suspend fun getCategoryById(id: Long): Category? = throw NotImplementedError()
    override suspend fun getCategoryBySystemKey(systemKey: String): Category? = null
    override suspend fun getCategoryByDimensionId(dimensionId: Long): Category? = null
    override fun observeCategoryById(id: Long): Flow<Category?> = throw NotImplementedError()
    override suspend fun archive(id: Long) { archived += id }

    override suspend fun insert(category: Category) = throw NotImplementedError()
    override suspend fun insertAll(categories: List<Category>) { insertedBatches += categories }
    override suspend fun update(category: Category) = throw NotImplementedError()
}

class FakeAccounts(private val hasYieldingAccount: Boolean) : IAccountRepository {
    override suspend fun hasYieldingAccount(): Boolean = hasYieldingAccount
    override fun observeAllAccounts(): Flow<List<Account>> = flowOf(emptyList())
    override suspend fun getAllAccounts(): List<Account> = emptyList()
    override suspend fun getAllAccountsIncludingClosed(): List<Account> = emptyList()
    override fun observeAllAccountsIncludingClosed(): Flow<List<Account>> = flowOf(emptyList())
    override suspend fun getAllLedgerAccounts(): List<Account> = emptyList()
    override fun observeAllLedgerAccounts(): Flow<List<Account>> = flowOf(emptyList())
    override suspend fun getAccountById(accountId: Long): Account? = null
    override fun observeAccountById(accountId: Long): Flow<Account?> = flowOf(null)
    override suspend fun getDefaultAccount(): Account? = null
    override fun observeDefaultAccount(): Flow<Account?> = flowOf(null)
    override suspend fun getAccountCount(): Int = 0
    override suspend fun insert(account: Account): Long = throw NotImplementedError()
    override suspend fun update(account: Account) = throw NotImplementedError()
    override suspend fun delete(account: Account) = throw NotImplementedError()
    override suspend fun reopen(accountId: Long) = throw NotImplementedError()
}

class FakeRecurring(private val hasRecurring: Boolean) : IRecurringRepository {
    override suspend fun hasRecurringForCategory(categoryId: Long) = hasRecurring
    override suspend fun hasTransactionForRecurring(recurringId: Long) = false
    override suspend fun hasRecurringForAccount(accountId: Long) = false
    override suspend fun hasRecurringForCreditCard(creditCardId: Long) = false
    override fun observeAllRecurring(): Flow<List<Recurring>> = flowOf(emptyList())
    override fun observeRecurringById(id: Long): Flow<Recurring?> = flowOf(null)
    override suspend fun getRecurringById(id: Long): Recurring? = null
    override suspend fun insert(recurring: Recurring) = throw NotImplementedError()
    override suspend fun update(recurring: Recurring) = throw NotImplementedError()
    override suspend fun delete(recurring: Recurring) = throw NotImplementedError()
}

class FakeBudget(private val hasBudget: Boolean) : IBudgetRepository {
    override suspend fun hasBudgetForCategory(categoryId: Long) = hasBudget
    override suspend fun hasBudgetForRecurring(recurringId: Long) = false
    override fun observeAllBudgets(): Flow<List<Budget>> = flowOf(emptyList())
    override suspend fun getAllBudgets(): List<Budget> = emptyList()
    override suspend fun insert(budget: Budget) = throw NotImplementedError()
    override suspend fun update(budget: Budget) = throw NotImplementedError()
    override suspend fun delete(budget: Budget) = throw NotImplementedError()
}

class FakeEntries(private val hasEntries: Boolean) : IEntryRepository {
    override suspend fun hasEntries(accountId: Long): Boolean = hasEntries
    override suspend fun hasEntriesForDimension(dimensionId: Long): Boolean = hasEntries
    override suspend fun balance(accountId: Long): Double = 0.0
    override fun observeLedgerChanges(): Flow<Unit> = flowOf(Unit)
    override suspend fun getEntriesByTransaction(transactionId: Long): List<Entry> = throw NotImplementedError()
    override fun observeEntriesByTransaction(transactionId: Long): Flow<List<Entry>> = throw NotImplementedError()
    override suspend fun accountFlows(month: YearMonth, accountId: Long, yieldDimensionId: Long?): AccountFlows = throw NotImplementedError()
    override suspend fun dimensionEntryCountInMonth(month: YearMonth, dimensionId: Long): Int = throw NotImplementedError()

    override suspend fun accountBalanceUpTo(accountId: Long, target: LocalDate): Double = throw NotImplementedError()
    override suspend fun balanceUpToByCurrency(target: YearMonth, excludedAccountIds: Set<Long>): MoneyByCurrency = throw NotImplementedError()
    override suspend fun naturalBalanceUpToByCurrency(target: YearMonth, type: AccountType, excludedAccountIds: Set<Long>): MoneyByCurrency = throw NotImplementedError()
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
