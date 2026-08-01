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
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.MoneyByCurrency
import com.neoutils.finsight.domain.repository.AssetMonthFlowsByCurrency
import com.neoutils.finsight.domain.repository.DimensionFlowsByCurrency
import com.neoutils.finsight.domain.repository.LiabilityMonthFlowsByCurrency
import com.neoutils.finsight.domain.repository.ScopeStatsByCurrency
import kotlinx.datetime.LocalDate

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
    getAccountCurrencies = FakeAccountCurrencies(),
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
     *
     * **And neither does anything else in that month**, which is the half this test used
     * to get wrong. It asserted `100.0` for the category that *could* be measured, over a
     * total built by summing only the measurable ones — a full bar reading "this is all
     * you spent" with a whole category outside the denominator. One hundred percent is as
     * much an assertion as zero, and it is worse here: the surviving figure is
     * single-currency, so it is exact and carries no mark to warn anyone. One figure
     * without a magnitude does not make a smaller whole; it makes the whole unknown.
     *
     * The ordering survives, because putting figures in order needs no whole.
     */
    @Test
    fun `one category no rate reaches leaves the whole month without shares`() = runTest {
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

        assertEquals(listOf(food, travel), result.map { it.category }, "ordering needs no whole")
        assertEquals(
            null,
            result[0].percentage,
            "measurable, but against an unknown whole — 100% would be an invention",
        )
        assertEquals(null, result[1].percentage, "no rate reaches it, so it has no share")
        assertEquals(60.0, result[0].value, "both figures are untouched")
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
    override suspend fun accountFlows(month: YearMonth, accountId: Long): com.neoutils.finsight.domain.repository.AccountFlows = throw NotImplementedError()
    override suspend fun dimensionEntryCountInMonth(month: YearMonth, dimensionId: Long): Int = throw NotImplementedError()

    override suspend fun accountBalanceUpTo(accountId: Long, target: YearMonth): Double = throw NotImplementedError()
    override suspend fun balanceUpToByCurrency(target: YearMonth): MoneyByCurrency = throw NotImplementedError()
    override suspend fun naturalBalanceUpToByCurrency(target: YearMonth, type: AccountType): MoneyByCurrency = throw NotImplementedError()
    override suspend fun dimensionOwedByCurrency(dimensionId: Long): MoneyByCurrency = throw NotImplementedError()
    override suspend fun dimensionFlowsByCurrency(dimensionId: Long): DimensionFlowsByCurrency = throw NotImplementedError()
    override suspend fun owedByDimensionByCurrency(dimensionIds: Collection<Long>): Map<Long, MoneyByCurrency> = throw NotImplementedError()
    override suspend fun flowsByDimensionByCurrency(dimensionIds: Collection<Long>): Map<Long, DimensionFlowsByCurrency> = throw NotImplementedError()
    override suspend fun liabilityMonthFlowsByCurrency(month: YearMonth): LiabilityMonthFlowsByCurrency = throw NotImplementedError()
    override suspend fun assetMonthFlowsByCurrency(month: YearMonth): AssetMonthFlowsByCurrency = throw NotImplementedError()
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

internal class FakeAccountCurrencies(
    private val inUse: List<String> = listOf("BRL"),
) : GetAccountCurrenciesUseCase {
    override suspend fun invoke() = AccountCurrencies(inUse = inUse, ofDefaultAccount = inUse.firstOrNull())
}
