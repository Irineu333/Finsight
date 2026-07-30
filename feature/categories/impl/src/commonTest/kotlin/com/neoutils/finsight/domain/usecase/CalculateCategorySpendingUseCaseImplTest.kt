package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.ExchangeRate
import com.neoutils.finsight.domain.repository.IBaseCurrencyRepository
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.repository.IExchangeRateRepository
import com.neoutils.finsight.ui.icons.CategoryLazyIcon
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val MONTH = YearMonth(2026, 1)

/**
 * The reducer, over an archive with no rate: every figure here is mono-currency, so it
 * comes out as its single exact term — which is what the assertions below read.
 */
private fun reducer(
    base: String = "BRL",
    rates: Map<String, Double> = emptyMap(),
) = ConsolidateMoneyUseCase(
    baseCurrencyRepository = FakeBaseCurrencyRepository(base),
    exchangeRateRepository = FakeExchangeRateRepository(rates),
)

/** The one value of a mono-currency figure. */
private val com.neoutils.finsight.domain.model.CategorySpending.value: Double
    get() = amount.terms.single().value

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
            consolidateMoney = reducer(),
        )

        val result = useCase(MONTH)

        assertEquals(listOf(food, transport), result.map { it.category }) // sorted desc by amount
        assertEquals(50.0, result[0].value)
        assertEquals(25.0, result[1].value)
        assertEquals(66.666, result[0].percentage!!, absoluteTolerance = 0.01) // 50 / 75
        assertEquals(33.333, result[1].percentage!!, absoluteTolerance = 0.01)
    }

    @Test
    fun `income inverts the credit-natured balance to read positive`() = runTest {
        val salary = category(3, Category.Type.INCOME, accountId = 20)
        val useCase = CalculateCategoryIncomeUseCaseImpl(
            categoryRepository = FakeCategoryRepository(listOf(salary)),
            // INCOME accounts are credit-natured: natural balance is negative.
            entryRepository = FakeEntryRepository(mapOf(20L to -80.0)),
            consolidateMoney = reducer(),
        )

        val result = useCase(MONTH)

        assertEquals(1, result.size)
        assertEquals(80.0, result[0].value)
    }

    @Test
    fun `categories with a zero balance are excluded`() = runTest {
        val posted = category(1, Category.Type.EXPENSE, accountId = 10)
        val neverPosted = category(2, Category.Type.EXPENSE, accountId = 11)
        val zero = category(3, Category.Type.EXPENSE, accountId = 12)
        val useCase = CalculateCategorySpendingUseCaseImpl(
            categoryRepository = FakeCategoryRepository(listOf(posted, neverPosted, zero)),
            entryRepository = FakeEntryRepository(mapOf(10L to 40.0, 12L to 0.0)),
            consolidateMoney = reducer(),
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
            consolidateMoney = reducer(),
        )

        val result = useCase(MONTH)

        assertTrue(result.all { it.category.type == Category.Type.EXPENSE })
        assertEquals(30.0, result.single().value)
    }

    /**
     * **Two currencies, one ranking.** The scale is the base, built from the same rates
     * the figures on screen are consolidated at — so the order the user reads and the
     * numbers beside it cannot disagree. With the dollar at 5, US$ 20 outranks R$ 60.
     */
    @Test
    fun `categories in different currencies are ranked on a common scale`() = runTest {
        val food = category(1, Category.Type.EXPENSE, accountId = 10)
        val travel = category(2, Category.Type.EXPENSE, accountId = 11)
        val useCase = CalculateCategorySpendingUseCaseImpl(
            categoryRepository = FakeCategoryRepository(listOf(food, travel)),
            entryRepository = FakeEntryRepository(
                multi = mapOf(10L to mapOf("BRL" to 60.0), 11L to mapOf("USD" to 20.0)),
            ),
            consolidateMoney = reducer(rates = mapOf("USD" to 5.0)),
        )

        val result = useCase(MONTH)

        assertEquals(listOf(travel, food), result.map { it.category }, "100 in base beats 60")
        assertEquals(62.5, result[0].percentage!!, absoluteTolerance = 0.01) // 100 / 160
        assertEquals(37.5, result[1].percentage!!, absoluteTolerance = 0.01) // 60 / 160
    }

    /**
     * A category whose currency no rate reaches has **no share** — not zero. Zero is an
     * assertion about how much of the total it is; the absence of a rate is the absence
     * of an answer (design D9). It still appears, with its own figure, and last.
     */
    @Test
    fun `a category no rate reaches has no percentage and sorts last`() = runTest {
        val food = category(1, Category.Type.EXPENSE, accountId = 10)
        val travel = category(2, Category.Type.EXPENSE, accountId = 11)
        val useCase = CalculateCategorySpendingUseCaseImpl(
            categoryRepository = FakeCategoryRepository(listOf(food, travel)),
            entryRepository = FakeEntryRepository(
                multi = mapOf(10L to mapOf("BRL" to 60.0), 11L to mapOf("JPY" to 5000.0)),
            ),
            consolidateMoney = reducer(),
        )

        val result = useCase(MONTH)

        assertEquals(listOf(food, travel), result.map { it.category })
        assertEquals(100.0, result[0].percentage!!, absoluteTolerance = 0.01)
        assertEquals(null, result[1].percentage, "no rate reaches it, so it has no share")
        assertEquals(5000.0, result[1].value, "and its own figure is untouched")
    }

    /**
     * The single-currency user, whatever their currency: no rate is read, the ranking is
     * the plain one, and the percentages are what they always were. The gate of 13.3
     * falls out of the construction rather than out of a special case.
     */
    @Test
    fun `a single currency that is not the base reads exactly as before`() = runTest {
        val food = category(1, Category.Type.EXPENSE, accountId = 10)
        val transport = category(2, Category.Type.EXPENSE, accountId = 11)
        val useCase = CalculateCategorySpendingUseCaseImpl(
            categoryRepository = FakeCategoryRepository(listOf(food, transport)),
            entryRepository = FakeEntryRepository(mapOf(10L to 50.0, 11L to 25.0), currency = "USD"),
            // A rate exists and is deliberately never consulted: there was nothing to
            // reconcile, so converting would trade an exact figure for an approximate one.
            consolidateMoney = reducer(rates = mapOf("USD" to 5.0)),
        )

        val result = useCase(MONTH)

        assertEquals(listOf(food, transport), result.map { it.category })
        assertEquals(66.666, result[0].percentage!!, absoluteTolerance = 0.01)
        assertEquals("USD", result[0].amount.terms.single().currency)
        assertEquals(false, result[0].amount.isApproximate)
    }
}


private class FakeBaseCurrencyRepository(base: String = "BRL") : IBaseCurrencyRepository {
    private val flow = MutableStateFlow(base)
    override fun observe(): StateFlow<String> = flow
    override suspend fun set(currency: String) { flow.value = currency }
}

private class FakeExchangeRateRepository(
    private val rates: Map<String, Double> = emptyMap(),
) : IExchangeRateRepository {
    override suspend fun rateAsOf(currency: String, date: kotlinx.datetime.LocalDate): ExchangeRate? =
        ratesAsOf(date)[currency]

    override suspend fun ratesAsOf(date: kotlinx.datetime.LocalDate): Map<String, ExchangeRate> =
        rates.mapValues { (currency, rate) ->
            ExchangeRate(
                currency = currency,
                date = date,
                rate = rate,
                source = ExchangeRate.Source.USER,
            )
        }
    override fun observeAll(): Flow<List<ExchangeRate>> = flowOf(emptyList())
    override suspend fun save(rate: ExchangeRate) = Unit
    override suspend fun remove(rate: ExchangeRate) = Unit
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

/**
 * @param balances the mono-currency case, in [currency] — the shape the app has until a
 * second currency is creatable.
 * @param multi dimensions whose entries genuinely sit in more than one currency.
 */
private class FakeEntryRepository(
    private val balances: Map<Long, Double> = emptyMap(),
    private val currency: String = "BRL",
    private val multi: Map<Long, Map<String, Double>> = emptyMap(),
) : IEntryRepository {
    override suspend fun dimensionBalanceInMonthByCurrency(month: YearMonth, dimensionId: Long) =
        multi[dimensionId]
            ?.let { com.neoutils.finsight.domain.model.MoneyByCurrency.of(it) }
            ?: balances[dimensionId]
                ?.let { com.neoutils.finsight.domain.model.MoneyByCurrency.of(currency, it) }
            ?: com.neoutils.finsight.domain.model.MoneyByCurrency.zero

    override suspend fun getEntriesByTransaction(transactionId: Long): List<com.neoutils.finsight.domain.model.Entry> = throw NotImplementedError()
    override fun observeEntriesByTransaction(transactionId: Long): kotlinx.coroutines.flow.Flow<List<com.neoutils.finsight.domain.model.Entry>> = throw NotImplementedError()
    override fun observeLedgerChanges(): Flow<Unit> = flowOf(Unit)
    override suspend fun balance(accountId: Long): Double = throw NotImplementedError()
    override suspend fun hasEntries(accountId: Long): Boolean = false
    override suspend fun hasEntriesForDimension(dimensionId: Long): Boolean = false
    override suspend fun dimensionBalanceInMonth(month: YearMonth, dimensionId: Long): Double = throw NotImplementedError()
    override suspend fun accountFlows(month: YearMonth, accountId: Long): com.neoutils.finsight.domain.repository.AccountFlows = throw NotImplementedError()
    override suspend fun dimensionEntryCountInMonth(month: YearMonth, dimensionId: Long): Int = throw NotImplementedError()
    override suspend fun balanceUpTo(target: YearMonth, accountId: Long?): Double = throw NotImplementedError()
    override suspend fun naturalBalanceUpTo(target: YearMonth, type: com.neoutils.finsight.domain.model.AccountType): Double = throw NotImplementedError()
    override suspend fun dimensionOwed(dimensionId: Long): Double = throw NotImplementedError()
    override suspend fun dimensionFlows(dimensionId: Long): com.neoutils.finsight.domain.repository.DimensionFlows = throw NotImplementedError()
    override suspend fun liabilityMonthFlows(month: YearMonth): com.neoutils.finsight.domain.repository.LiabilityMonthFlows = throw NotImplementedError()
    override suspend fun assetMonthFlows(month: YearMonth): com.neoutils.finsight.domain.repository.AssetMonthFlows = throw NotImplementedError()
    override suspend fun totalsByDimension(
        categoryType: com.neoutils.finsight.domain.model.AccountType,
        startDate: kotlinx.datetime.LocalDate,
        endDate: kotlinx.datetime.LocalDate,
        siblingAccountIds: List<Long>,
    ): Map<Long?, Double> = throw NotImplementedError()
    override suspend fun totalsByDimensionInScope(
        categoryType: com.neoutils.finsight.domain.model.AccountType,
        scopeDimensionIds: List<Long>,
    ): Map<Long?, Double> = throw NotImplementedError()
    override suspend fun scopeStats(scopeAccountIds: List<Long>, startDate: kotlinx.datetime.LocalDate, endDate: kotlinx.datetime.LocalDate): com.neoutils.finsight.domain.repository.ScopeStats = throw NotImplementedError()
}
