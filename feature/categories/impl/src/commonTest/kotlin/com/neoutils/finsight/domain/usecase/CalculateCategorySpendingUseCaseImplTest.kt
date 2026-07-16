package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.ui.icons.CategoryLazyIcon
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val MONTH = YearMonth(2026, 1)

class CalculateCategorySpendingUseCaseImplTest {

    private fun category(id: Long, type: Category.Type, accountId: Long?) = Category(
        id = id,
        name = "cat$id",
        icon = CategoryLazyIcon("icon"),
        type = type,
        createdAt = 0,
        accountId = accountId,
    )

    @Test
    fun `spending sums entries per expense category with share and descending order`() = runTest {
        val food = category(1, Category.Type.EXPENSE, accountId = 10)
        val transport = category(2, Category.Type.EXPENSE, accountId = 11)
        val useCase = CalculateCategorySpendingUseCaseImpl(
            categoryRepository = FakeCategoryRepository(listOf(food, transport)),
            // EXPENSE accounts are debit-natured: balanceInMonth is already +spent.
            entryRepository = FakeEntryRepository(mapOf(10L to 50.0, 11L to 25.0)),
        )

        val result = useCase(MONTH)

        assertEquals(listOf(food, transport), result.map { it.category }) // sorted desc by amount
        assertEquals(50.0, result[0].amount)
        assertEquals(25.0, result[1].amount)
        assertEquals(66.666, result[0].percentage, absoluteTolerance = 0.01) // 50 / 75
        assertEquals(33.333, result[1].percentage, absoluteTolerance = 0.01)
    }

    @Test
    fun `income inverts the credit-natured balance to read positive`() = runTest {
        val salary = category(3, Category.Type.INCOME, accountId = 20)
        val useCase = CalculateCategoryIncomeUseCaseImpl(
            categoryRepository = FakeCategoryRepository(listOf(salary)),
            // INCOME accounts are credit-natured: natural balance is negative.
            entryRepository = FakeEntryRepository(mapOf(20L to -80.0)),
        )

        val result = useCase(MONTH)

        assertEquals(1, result.size)
        assertEquals(80.0, result[0].amount)
    }

    @Test
    fun `categories with no ledger account or zero balance are excluded`() = runTest {
        val posted = category(1, Category.Type.EXPENSE, accountId = 10)
        val neverPosted = category(2, Category.Type.EXPENSE, accountId = null)
        val zero = category(3, Category.Type.EXPENSE, accountId = 12)
        val useCase = CalculateCategorySpendingUseCaseImpl(
            categoryRepository = FakeCategoryRepository(listOf(posted, neverPosted, zero)),
            entryRepository = FakeEntryRepository(mapOf(10L to 40.0, 12L to 0.0)),
        )

        val result = useCase(MONTH)

        assertEquals(listOf(posted), result.map { it.category })
    }

    @Test
    fun `only expense categories are considered for spending`() = runTest {
        val food = category(1, Category.Type.EXPENSE, accountId = 10)
        val salary = category(2, Category.Type.INCOME, accountId = 20)
        val useCase = CalculateCategorySpendingUseCaseImpl(
            categoryRepository = FakeCategoryRepository(listOf(food, salary)),
            entryRepository = FakeEntryRepository(mapOf(10L to 30.0, 20L to -99.0)),
        )

        val result = useCase(MONTH)

        assertTrue(result.all { it.category.type == Category.Type.EXPENSE })
        assertEquals(30.0, result.single().amount)
    }
}

private class FakeCategoryRepository(private val categories: List<Category>) : ICategoryRepository {
    override suspend fun getAllCategories(): List<Category> = categories
    override fun observeAllCategories(): Flow<List<Category>> = throw NotImplementedError()
    override fun observeCategoriesByType(type: Category.Type): Flow<List<Category>> = throw NotImplementedError()
    override suspend fun getCategoryById(id: Long): Category? = categories.firstOrNull { it.id == id }
    override fun observeCategoryById(id: Long): Flow<Category?> = throw NotImplementedError()
    override suspend fun insert(category: Category) = throw NotImplementedError()
    override suspend fun update(category: Category) = throw NotImplementedError()
    override suspend fun delete(category: Category) = throw NotImplementedError()
}

private class FakeEntryRepository(private val balances: Map<Long, Double>) : IEntryRepository {
    override suspend fun balanceInMonth(month: YearMonth, accountId: Long): Double = balances[accountId] ?: 0.0
    override suspend fun balanceUpTo(target: YearMonth, accountId: Long?): Double = throw NotImplementedError()
    override suspend fun invoiceOwed(invoiceId: Long): Double = throw NotImplementedError()
    override suspend fun netWorth(): Double = throw NotImplementedError()
    override suspend fun categoryTotals(
        categoryType: com.neoutils.finsight.domain.model.AccountType,
        startDate: kotlinx.datetime.LocalDate,
        endDate: kotlinx.datetime.LocalDate,
        siblingAccountIds: List<Long>,
    ): Map<Long, Double> = throw NotImplementedError()
}
