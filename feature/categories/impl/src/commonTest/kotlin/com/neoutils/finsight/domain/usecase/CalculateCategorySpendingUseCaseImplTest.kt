package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.ExchangeRate
import com.neoutils.finsight.domain.repository.IExchangeRateRepository
import kotlinx.datetime.LocalDate
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.test.StubEntryRepository
import com.neoutils.finsight.test.brl
import com.neoutils.finsight.ui.icons.CategoryLazyIcon
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.YearMonth

private val MONTH = YearMonth(2026, 1)
private val TODAY = LocalDate(2026, 2, 15)

class CalculateCategorySpendingUseCaseImplTest {

    private fun category(id: Long, type: Category.Type, accountId: Long) = Category(
        id = id,
        name = "cat$id",
        icon = CategoryLazyIcon("icon"),
        type = type,
        createdAt = 0,
        dimensionId = accountId,
    )

    @Test
    fun `spending sums entries per expense category with share and descending order`() = runTest {
        val food = category(1, Category.Type.EXPENSE, accountId = 10)
        val transport = category(2, Category.Type.EXPENSE, accountId = 11)
        val useCase = CalculateCategorySpendingUseCaseImpl(
            categoryRepository = FakeCategoryRepository(listOf(food, transport)),
            // EXPENSE accounts are debit-natured: balanceInMonth is already +spent.
            entryRepository = FakeEntryRepository(mapOf(10L to 50.0, 11L to 25.0)),
            consolidateFigure = ConsolidateFigureUseCase(NoRates),
        )

        val result = useCase(MONTH, base = "BRL", today = TODAY)

        assertEquals(listOf(food, transport), result.map { it.category }) // sorted desc by amount
        assertEquals(50.0, result[0].amount.comparable)
        assertEquals(25.0, result[1].amount.comparable)
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
            consolidateFigure = ConsolidateFigureUseCase(NoRates),
        )

        val result = useCase(MONTH, base = "BRL", today = TODAY)

        assertEquals(1, result.size)
        assertEquals(80.0, result[0].amount.comparable)
    }

    @Test
    fun `categories with a zero balance are excluded`() = runTest {
        val posted = category(1, Category.Type.EXPENSE, accountId = 10)
        val neverPosted = category(2, Category.Type.EXPENSE, accountId = 11)
        val zero = category(3, Category.Type.EXPENSE, accountId = 12)
        val useCase = CalculateCategorySpendingUseCaseImpl(
            categoryRepository = FakeCategoryRepository(listOf(posted, neverPosted, zero)),
            entryRepository = FakeEntryRepository(mapOf(10L to 40.0, 12L to 0.0)),
            consolidateFigure = ConsolidateFigureUseCase(NoRates),
        )

        val result = useCase(MONTH, base = "BRL", today = TODAY)

        assertEquals(listOf(posted), result.map { it.category })
    }

    @Test
    fun `only expense categories are considered for spending`() = runTest {
        val food = category(1, Category.Type.EXPENSE, accountId = 10)
        val salary = category(2, Category.Type.INCOME, accountId = 20)
        val useCase = CalculateCategorySpendingUseCaseImpl(
            categoryRepository = FakeCategoryRepository(listOf(food, salary)),
            entryRepository = FakeEntryRepository(mapOf(10L to 30.0, 20L to -99.0)),
            consolidateFigure = ConsolidateFigureUseCase(NoRates),
        )

        val result = useCase(MONTH, base = "BRL", today = TODAY)

        assertTrue(result.all { it.category.type == Category.Type.EXPENSE })
        assertEquals(30.0, result.single().amount.comparable)
    }
}

private class FakeCategoryRepository(private val categories: List<Category>) : ICategoryRepository {
    override suspend fun getAllCategories(): List<Category> = categories
    override suspend fun getAllCategoriesIncludingClosed(): List<Category> = getAllCategories()
    override fun observeAllCategoriesIncludingClosed(): Flow<List<Category>> = observeAllCategories()
    override fun observeAllCategories(): Flow<List<Category>> = throw NotImplementedError()
    override fun observeCategoriesByType(type: Category.Type): Flow<List<Category>> = throw NotImplementedError()
    override suspend fun getCategoryById(id: Long): Category? = categories.firstOrNull { it.id == id }
    override suspend fun getCategoryByDimensionId(dimensionId: Long): Category? = null
    override fun observeCategoryById(id: Long): Flow<Category?> = throw NotImplementedError()
    override suspend fun archive(id: Long) = Unit
    override suspend fun unarchive(id: Long) = Unit
    override suspend fun existsByName(name: String, ignoreId: Long): Boolean = false

    override suspend fun insert(category: Category) = throw NotImplementedError()
    override suspend fun insertAll(categories: List<Category>) = throw NotImplementedError()
    override suspend fun update(category: Category) = throw NotImplementedError()
    override suspend fun delete(category: Category) = throw NotImplementedError()
}

private class FakeEntryRepository(private val balances: Map<Long, Double>) : StubEntryRepository() {
    override fun observeLedgerChanges(): Flow<Unit> = flowOf(Unit)
    override suspend fun hasEntries(accountId: Long): Boolean = false
    override suspend fun hasEntriesForDimension(dimensionId: Long): Boolean = false
    override suspend fun dimensionBalanceInMonth(month: YearMonth, dimensionId: Long) = brl(balances[dimensionId] ?: 0.0)
}

/** No rate at all — the single-currency profile these cases exercise. */
internal object NoRates : IExchangeRateRepository {
    override suspend fun rateOn(currency: String, date: LocalDate) = null
    override fun observeAll() = throw NotImplementedError()
    override suspend fun getAll() = throw NotImplementedError()
    override suspend fun record(rate: ExchangeRate) = throw NotImplementedError()
    override suspend fun remove(rate: ExchangeRate) = throw NotImplementedError()
}
