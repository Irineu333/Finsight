package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.error.AccountError
import com.neoutils.finsight.domain.exception.AccountException
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Budget
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.repository.AccountFlows
import com.neoutils.finsight.domain.repository.CardMonthFlows
import com.neoutils.finsight.domain.repository.IBudgetRepository
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.repository.IRecurringRepository
import com.neoutils.finsight.domain.repository.InvoiceFlows
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
        repo: RecordingCategoryRepository = RecordingCategoryRepository(),
    ) = DeleteCategoryUseCase(
        categoryRepository = repo,
        entryRepository = FakeEntries(hasEntries),
        recurringRepository = FakeRecurring(hasRecurring),
        budgetRepository = FakeBudget(hasBudget),
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
        val error = assertIs<AccountException>(useCase(hasEntries = true, repo = repo)(category).leftOrNull())
        assertEquals(AccountError.HAS_TRANSACTIONS, error.error)
        assertTrue(repo.deleted.isEmpty())
    }

    @Test
    fun `a category still in a budget is refused`() = runTest {
        // budget_categories is CASCADE: deleting would strip it from the budget.
        val repo = RecordingCategoryRepository()
        val error = assertIs<AccountException>(useCase(hasBudget = true, repo = repo)(category).leftOrNull())
        assertEquals(AccountError.HAS_BUDGET, error.error)
        assertTrue(repo.deleted.isEmpty(), "nothing may be removed")
    }

    @Test
    fun `a category a recurring still points at is refused`() = runTest {
        // recurring.categoryId is SET_NULL: the template would survive uncategorized.
        val repo = RecordingCategoryRepository()
        val error = assertIs<AccountException>(useCase(hasRecurring = true, repo = repo)(category).leftOrNull())
        assertEquals(AccountError.HAS_RECURRING, error.error)
        assertTrue(repo.deleted.isEmpty())
    }
}

private class RecordingCategoryRepository : ICategoryRepository {
    val deleted = mutableListOf<Long>()
    override suspend fun delete(category: Category) { deleted += category.id }
    override fun observeAllCategories(): Flow<List<Category>> = flowOf(emptyList())
    override suspend fun getAllCategories(): List<Category> = emptyList()
    override suspend fun getAllCategoriesIncludingClosed(): List<Category> = emptyList()
    override fun observeAllCategoriesIncludingClosed(): Flow<List<Category>> = flowOf(emptyList())
    override fun observeCategoriesByType(type: Category.Type): Flow<List<Category>> = flowOf(emptyList())
    override suspend fun getCategoryById(id: Long): Category? = throw NotImplementedError()
    override fun observeCategoryById(id: Long): Flow<Category?> = throw NotImplementedError()
    override suspend fun archive(id: Long) = Unit

    override suspend fun insert(category: Category) = throw NotImplementedError()
    override suspend fun update(category: Category) = throw NotImplementedError()
}

private class FakeRecurring(private val hasRecurring: Boolean) : IRecurringRepository {
    override suspend fun hasRecurringForCategory(categoryId: Long) = hasRecurring
    override suspend fun hasRecurringForAccount(accountId: Long) = false
    override suspend fun hasRecurringForCreditCard(creditCardId: Long) = false
    override fun observeAllRecurring(): Flow<List<Recurring>> = flowOf(emptyList())
    override fun observeRecurringById(id: Long): Flow<Recurring?> = flowOf(null)
    override suspend fun getRecurringById(id: Long): Recurring? = null
    override suspend fun insert(recurring: Recurring) = throw NotImplementedError()
    override suspend fun update(recurring: Recurring) = throw NotImplementedError()
    override suspend fun delete(recurring: Recurring) = throw NotImplementedError()
}

private class FakeBudget(private val hasBudget: Boolean) : IBudgetRepository {
    override suspend fun hasBudgetForCategory(categoryId: Long) = hasBudget
    override fun observeAllBudgets(): Flow<List<Budget>> = flowOf(emptyList())
    override suspend fun getAllBudgets(): List<Budget> = emptyList()
    override suspend fun insert(budget: Budget) = throw NotImplementedError()
    override suspend fun update(budget: Budget) = throw NotImplementedError()
    override suspend fun delete(budget: Budget) = throw NotImplementedError()
}

private class FakeEntries(private val hasEntries: Boolean) : IEntryRepository {
    override suspend fun hasEntries(accountId: Long): Boolean = hasEntries
    override suspend fun hasEntriesForDimension(dimensionId: Long): Boolean = hasEntries
    override suspend fun balance(accountId: Long): Double = 0.0
    override fun observeLedgerChanges(): Flow<Unit> = flowOf(Unit)
    override suspend fun getEntriesByTransaction(transactionId: Long): List<Entry> = throw NotImplementedError()
    override fun observeEntriesByTransaction(transactionId: Long): Flow<List<Entry>> = throw NotImplementedError()
    override suspend fun balanceUpTo(target: YearMonth, accountId: Long?): Double = throw NotImplementedError()
    override suspend fun dimensionBalanceInMonth(month: YearMonth, dimensionId: Long): Double = throw NotImplementedError()
    override suspend fun accountFlows(month: YearMonth, accountId: Long): AccountFlows = throw NotImplementedError()
    override suspend fun dimensionEntryCountInMonth(month: YearMonth, dimensionId: Long): Int = throw NotImplementedError()
    override suspend fun dimensionOwed(dimensionId: Long): Double = throw NotImplementedError()
    override suspend fun dimensionFlows(dimensionId: Long): InvoiceFlows = throw NotImplementedError()
    override suspend fun cardMonthFlows(month: YearMonth): CardMonthFlows = throw NotImplementedError()
    override suspend fun netWorth(): Double = throw NotImplementedError()
    override suspend fun totalsByDimension(
        nominalType: AccountType,
        startDate: LocalDate,
        endDate: LocalDate,
        siblingAccountIds: List<Long>,
    ): Map<Long?, Double> = throw NotImplementedError()
    override suspend fun totalsByDimensionInScope(
        nominalType: AccountType,
        scopeDimensionIds: List<Long>,
    ): Map<Long?, Double> = throw NotImplementedError()
    override suspend fun reportStats(scopeAccountIds: List<Long>, startDate: LocalDate, endDate: LocalDate): com.neoutils.finsight.domain.repository.ReportStats = throw NotImplementedError()
}
