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
import com.neoutils.finsight.domain.model.SpendingSubject
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

    private fun category(id: Long, type: Category.Type, dimensionId: Long) = Category(
        id = id,
        name = "cat$id",
        icon = CategoryLazyIcon("icon"),
        type = type,
        createdAt = 0,
        dimensionId = dimensionId,
    )

    /** One nominal nature's whole month, mono-currency — the shape most cases need. */
    private fun ledger(
        nominalType: AccountType,
        vararg totals: Pair<Long?, Double>,
        currency: String = "BRL",
    ) = FakeEntryRepository(
        mapOf(nominalType to totals.toMap().mapValues { MoneyByCurrency.of(currency, it.value) })
    )

    /** The same, for dimensions whose entries genuinely sit in more than one currency. */
    private fun multiCurrencyLedger(
        nominalType: AccountType,
        vararg totals: Pair<Long?, Map<String, Double>>,
    ) = FakeEntryRepository(
        mapOf(nominalType to totals.toMap().mapValues { MoneyByCurrency.of(it.value) })
    )

    private fun spending(categories: List<Category>, entries: FakeEntryRepository, rates: Map<String, Double> = emptyMap()) =
        CalculateCategorySpendingUseCaseImpl(
            categoryRepository = FakeCategoryRepository(categories),
            entryRepository = entries,
            consolidateMoney = reducer(rates = rates),
        )

    @Test
    fun `spending sums entries per expense category with share and descending order`() = runTest {
        val food = category(1, Category.Type.EXPENSE, dimensionId = 10)
        val transport = category(2, Category.Type.EXPENSE, dimensionId = 11)
        // EXPENSE accounts are debit-natured: the aggregate is already +spent.
        val useCase = spending(listOf(food, transport), ledger(AccountType.EXPENSE, 10L to 50.0, 11L to 25.0))

        val result = useCase(MONTH)

        assertEquals(listOf(food, transport), result.map { it.categoryOrNull() }) // sorted desc by amount
        assertEquals(50.0, result[0].value)
        assertEquals(25.0, result[1].value)
        assertEquals(66.666, result[0].percentage!!, absoluteTolerance = 0.01) // 50 / 75
        assertEquals(33.333, result[1].percentage!!, absoluteTolerance = 0.01)
    }

    @Test
    fun `income inverts the credit-natured balance to read positive`() = runTest {
        val salary = category(3, Category.Type.INCOME, dimensionId = 20)
        val useCase = CalculateCategoryIncomeUseCaseImpl(
            categoryRepository = FakeCategoryRepository(listOf(salary)),
            // INCOME accounts are credit-natured: the natural total is negative.
            entryRepository = ledger(AccountType.INCOME, 20L to -80.0),
            consolidateMoney = reducer(),
        )

        val result = useCase(MONTH)

        assertEquals(1, result.size)
        assertEquals(80.0, result[0].value)
    }

    @Test
    fun `categories with a zero balance are excluded`() = runTest {
        val posted = category(1, Category.Type.EXPENSE, dimensionId = 10)
        val neverPosted = category(2, Category.Type.EXPENSE, dimensionId = 11)
        val zero = category(3, Category.Type.EXPENSE, dimensionId = 12)
        val useCase = spending(
            listOf(posted, neverPosted, zero),
            ledger(AccountType.EXPENSE, 10L to 40.0, 12L to 0.0),
        )

        val result = useCase(MONTH)

        assertEquals(listOf(posted), result.map { it.categoryOrNull() })
    }

    @Test
    fun `only expense categories are considered for spending`() = runTest {
        val food = category(1, Category.Type.EXPENSE, dimensionId = 10)
        val salary = category(2, Category.Type.INCOME, dimensionId = 20)
        val useCase = spending(listOf(food, salary), ledger(AccountType.EXPENSE, 10L to 30.0, 20L to -99.0))

        val result = useCase(MONTH)

        assertTrue(result.all { it.categoryOrNull()?.type == Category.Type.EXPENSE })
        assertEquals(30.0, result.single().value)
    }

    /**
     * **Two currencies, one ranking.** The scale is the base, built from the same rates
     * the figures on screen are consolidated at — so the order the user reads and the
     * numbers beside it cannot disagree. With the dollar at 5, US$ 20 outranks R$ 60.
     */
    @Test
    fun `categories in different currencies are ranked on a common scale`() = runTest {
        val food = category(1, Category.Type.EXPENSE, dimensionId = 10)
        val travel = category(2, Category.Type.EXPENSE, dimensionId = 11)
        val useCase = spending(
            listOf(food, travel),
            multiCurrencyLedger(
                AccountType.EXPENSE,
                10L to mapOf("BRL" to 60.0),
                11L to mapOf("USD" to 20.0),
            ),
            rates = mapOf("USD" to 5.0),
        )

        val result = useCase(MONTH)

        assertEquals(listOf(travel, food), result.map { it.categoryOrNull() }, "100 in base beats 60")
        assertEquals(62.5, result[0].percentage!!, absoluteTolerance = 0.01) // 100 / 160
        assertEquals(37.5, result[1].percentage!!, absoluteTolerance = 0.01) // 60 / 160
    }

    /**
     * A category whose currency no rate reaches has **no share** — not zero. Zero is an
     * assertion about how much of the total it is; the absence of a rate is the absence
     * of an answer (design D9). It still appears, with its own figure, and last.
     *
     * **And neither does anything else in that month.** One figure without a magnitude
     * does not make a smaller whole; it makes the whole unknown, and a share over the
     * measurable ones alone would read "this is all you spent" with a whole category
     * outside the denominator. The ordering survives, because putting figures in order
     * needs no whole.
     */
    @Test
    fun `one category no rate reaches leaves the whole month without shares`() = runTest {
        val food = category(1, Category.Type.EXPENSE, dimensionId = 10)
        val travel = category(2, Category.Type.EXPENSE, dimensionId = 11)
        val useCase = spending(
            listOf(food, travel),
            multiCurrencyLedger(
                AccountType.EXPENSE,
                10L to mapOf("BRL" to 60.0),
                11L to mapOf("JPY" to 5000.0),
            ),
        )

        val result = useCase(MONTH)

        assertEquals(listOf(food, travel), result.map { it.categoryOrNull() }, "ordering needs no whole")
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
     * the plain one, and the percentages are what they always were.
     */
    @Test
    fun `a single currency that is not the base reads exactly as before`() = runTest {
        val food = category(1, Category.Type.EXPENSE, dimensionId = 10)
        val transport = category(2, Category.Type.EXPENSE, dimensionId = 11)
        val useCase = spending(
            listOf(food, transport),
            ledger(AccountType.EXPENSE, 10L to 50.0, 11L to 25.0, currency = "USD"),
            // A rate exists and is deliberately never consulted: there was nothing to
            // reconcile, so converting would trade an exact figure for an approximate one.
            rates = mapOf("USD" to 5.0),
        )

        val result = useCase(MONTH)

        assertEquals(listOf(food, transport), result.map { it.categoryOrNull() })
        assertEquals(66.666, result[0].percentage!!, absoluteTolerance = 0.01)
        assertEquals("USD", result[0].amount.terms.single().currency)
        assertEquals(false, result[0].amount.isApproximate)
    }

    // --- the unclassified group ---

    @Test
    fun `spending with no category is the last line of the breakdown`() = runTest {
        val food = category(1, Category.Type.EXPENSE, dimensionId = 10)
        val transport = category(2, Category.Type.EXPENSE, dimensionId = 11)
        // Bigger than either category, and still last.
        val useCase = spending(
            listOf(food, transport),
            ledger(AccountType.EXPENSE, 10L to 50.0, 11L to 25.0, null to 125.0),
        )

        val result = useCase(MONTH)

        assertEquals(
            listOf(SpendingSubject.Categorized(food), SpendingSubject.Categorized(transport), SpendingSubject.Uncategorized),
            result.map { it.subject },
        )
        assertEquals(125.0, result.last().value)
        assertEquals(62.5, result.last().percentage!!, absoluteTolerance = 0.01) // 125 / 200
    }

    /**
     * The sentence the whole feature exists to make true: the money the user reads on the
     * category's line does not move, and its share stops describing a whole that was not
     * the whole.
     */
    @Test
    fun `a category that read 100 percent shrinks without its amount moving`() = runTest {
        val food = category(1, Category.Type.EXPENSE, dimensionId = 10)

        val before = spending(listOf(food), ledger(AccountType.EXPENSE, 10L to 60.0))(MONTH).single()
        val after = spending(listOf(food), ledger(AccountType.EXPENSE, 10L to 60.0, null to 40.0))(MONTH).first()

        assertEquals(100.0, before.percentage)
        assertEquals(60.0, after.percentage)
        assertEquals(before.amount, after.amount)
    }

    @Test
    fun `a fully classified month has no unclassified line`() = runTest {
        val food = category(1, Category.Type.EXPENSE, dimensionId = 10)
        val useCase = spending(listOf(food), ledger(AccountType.EXPENSE, 10L to 60.0))

        assertEquals(listOf(SpendingSubject.Categorized(food)), useCase(MONTH).map { it.subject })
    }

    @Test
    fun `income with no category lands in the income breakdown, not the spending one`() = runTest {
        val salary = category(3, Category.Type.INCOME, dimensionId = 20)
        val food = category(1, Category.Type.EXPENSE, dimensionId = 10)
        val categories = listOf(salary, food)
        val entries = FakeEntryRepository(
            mapOf(
                AccountType.EXPENSE to mapOf(10L to MoneyByCurrency.of("BRL", 30.0)),
                AccountType.INCOME to mapOf<Long?, MoneyByCurrency>(
                    20L to MoneyByCurrency.of("BRL", -3_000.0),
                    null to MoneyByCurrency.of("BRL", -1_000.0),
                ),
            )
        )

        val income = CalculateCategoryIncomeUseCaseImpl(
            categoryRepository = FakeCategoryRepository(categories),
            entryRepository = entries,
            consolidateMoney = reducer(),
        )(MONTH)
        val expense = spending(categories, entries)(MONTH)

        assertEquals(
            listOf(SpendingSubject.Categorized(salary), SpendingSubject.Uncategorized),
            income.map { it.subject },
        )
        assertEquals(1_000.0, income.last().value, "credit-natured, read positive")
        assertEquals(
            listOf(SpendingSubject.Categorized(food)),
            expense.map { it.subject },
            "each breakdown sees only its own nature's unclassified total",
        )
    }

    /**
     * A card purchase with no category posts its EXPENSE leg with no dimension — the
     * invoice's dimension lands on the LIABILITY leg — so it belongs to the unclassified
     * total exactly like any other. What makes it reachable is that this use case reads
     * the month aggregate of the nominal nature and **not** the perspective-scoped read,
     * whose sibling accounts would drop a purchase with no asset leg (design D5). The
     * fake throws on every other read, so the perimeter is asserted by construction.
     */
    @Test
    fun `a card purchase with no category is part of the unclassified total`() = runTest {
        val food = category(1, Category.Type.EXPENSE, dimensionId = 10)
        val useCase = spending(
            listOf(food),
            ledger(AccountType.EXPENSE, 10L to 50.0, null to 30.0),
        )

        val result = useCase(MONTH)

        assertEquals(SpendingSubject.Uncategorized, result.last().subject)
        assertEquals(30.0, result.last().value)
    }

    /**
     * A dimension resolving to no category is an integrity failure, not an absence of
     * classification: it drops out rather than being washed into the bucket (design D7).
     */
    @Test
    fun `an orphan dimension is dropped, not folded into the unclassified total`() = runTest {
        val food = category(1, Category.Type.EXPENSE, dimensionId = 10)
        val useCase = spending(
            listOf(food),
            ledger(AccountType.EXPENSE, 10L to 50.0, 999L to 70.0, null to 50.0),
        )

        val result = useCase(MONTH)

        assertEquals(
            listOf(SpendingSubject.Categorized(food), SpendingSubject.Uncategorized),
            result.map { it.subject },
        )
        assertEquals(50.0, result.last().value, "the orphan is nowhere in the breakdown")
        assertEquals(50.0, result.first().percentage, "and it is not in the denominator either")
    }
}

/** The category of a line, or `null` when the line is the unclassified one. */
private fun com.neoutils.finsight.domain.model.CategorySpending.categoryOrNull(): Category? =
    (subject as? SpendingSubject.Categorized)?.category

private class FakeBaseCurrencyRepository(base: String = "BRL") : IBaseCurrencyRepository {
    private val flow = MutableStateFlow(base)
    override fun observe(): StateFlow<String> = flow
    override suspend fun set(code: String) { flow.value = code }
}

private class FakeExchangeRateRepository(
    private val rates: Map<String, Double> = emptyMap(),
    private val base: String = "BRL",
) : IExchangeRateRepository {
    override suspend fun rateAsOf(currency: String, date: kotlinx.datetime.LocalDate): ExchangeRate? =
        ratesAsOf(date)[currency]

    override suspend fun ratesAsOf(date: kotlinx.datetime.LocalDate): Map<String, ExchangeRate> =
        rates.mapValues { (currency, rate) ->
            ExchangeRate(
                currency = currency,
                counterCurrency = base,
                date = date,
                rate = rate,
                source = ExchangeRate.Source.USER,
            )
        }

    override suspend fun rateBetween(from: String, to: String, date: kotlinx.datetime.LocalDate) =
        ratesAsOf(date)[from]?.takeIf { it.counterCurrency == to }

    override fun observeAll(): Flow<List<ExchangeRate>> = flowOf(emptyList())
    override suspend fun save(rate: ExchangeRate) = Unit
    override suspend fun remove(rate: ExchangeRate) = Unit
    override suspend fun countNaming(currency: String) = 0
}

private class FakeCategoryRepository(private val categories: List<Category>) : ICategoryRepository {
    override suspend fun getAllCategories(): List<Category> = categories
    override suspend fun getAllCategoriesIncludingClosed(): List<Category> = getAllCategories()
    override fun observeAllCategoriesIncludingClosed(): Flow<List<Category>> = observeAllCategories()
    override fun observeAllCategories(): Flow<List<Category>> = throw NotImplementedError()
    override fun observeCategoriesByType(type: Category.Type): Flow<List<Category>> = throw NotImplementedError()
    override suspend fun getCategoryById(id: Long): Category? = categories.firstOrNull { it.id == id }
    override suspend fun getCategoryBySystemKey(systemKey: String): Category? = null
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
 * The ledger as one grouped read per nominal nature, which is what the use case now
 * asks for. The `null` key is the unclassified group of that same aggregate.
 *
 * [dimensionBalanceInMonthByCurrency] throws on purpose: the breakdown must cost one
 * read and not N, and a fake that answered both would let the old shape come back
 * unnoticed.
 */
private class FakeEntryRepository(
    private val totals: Map<AccountType, Map<Long?, MoneyByCurrency>> = emptyMap(),
) : IEntryRepository {
    override suspend fun totalsByDimensionInMonthByCurrency(
        month: YearMonth,
        nominalType: AccountType,
    ): Map<Long?, MoneyByCurrency> = totals[nominalType].orEmpty()

    override suspend fun dimensionMonthlySeriesByCurrency(dimensionId: Long, upTo: YearMonth): Map<YearMonth, MoneyByCurrency> = throw NotImplementedError()
    override suspend fun dimensionBalanceInMonthByCurrency(month: YearMonth, dimensionId: Long) =
        throw NotImplementedError()

    override suspend fun getEntriesByTransaction(transactionId: Long): List<com.neoutils.finsight.domain.model.Entry> = throw NotImplementedError()
    override fun observeEntriesByTransaction(transactionId: Long): kotlinx.coroutines.flow.Flow<List<com.neoutils.finsight.domain.model.Entry>> = throw NotImplementedError()
    override fun observeLedgerChanges(): Flow<Unit> = flowOf(Unit)
    override suspend fun balance(accountId: Long): Double = throw NotImplementedError()
    override suspend fun hasEntries(accountId: Long): Boolean = false
    override suspend fun hasEntriesForDimension(dimensionId: Long): Boolean = false
    override suspend fun accountFlows(month: YearMonth, accountId: Long, yieldDimensionId: Long?): com.neoutils.finsight.domain.repository.AccountFlows = throw NotImplementedError()

    override suspend fun accountBalanceUpTo(accountId: Long, target: LocalDate): Double = throw NotImplementedError()
    override suspend fun balanceUpToByCurrency(target: YearMonth, excludedAccountIds: Set<Long>): MoneyByCurrency = throw NotImplementedError()
    override suspend fun naturalBalanceUpToByCurrency(target: YearMonth, type: AccountType, excludedAccountIds: Set<Long>): MoneyByCurrency = throw NotImplementedError()
    override suspend fun dimensionOwedByCurrency(dimensionId: Long): MoneyByCurrency = throw NotImplementedError()
    override suspend fun dimensionFlowsByCurrency(dimensionId: Long): DimensionFlowsByCurrency = throw NotImplementedError()
    override suspend fun owedByDimensionByCurrency(dimensionIds: Collection<Long>): Map<Long, MoneyByCurrency> = throw NotImplementedError()
    override suspend fun flowsByDimensionByCurrency(dimensionIds: Collection<Long>): Map<Long, DimensionFlowsByCurrency> = throw NotImplementedError()
    override suspend fun liabilityMonthFlowsByCurrency(month: YearMonth): LiabilityMonthFlowsByCurrency = throw NotImplementedError()
    override suspend fun netWorthByCurrency(): MoneyByCurrency = throw NotImplementedError()
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

internal class FakeAccountCurrencies(
    private val inUse: List<String> = listOf("BRL"),
) : GetAccountCurrenciesUseCase {
    override suspend fun invoke() = AccountCurrencies(inUse = inUse, ofDefaultAccount = inUse.firstOrNull())
}
