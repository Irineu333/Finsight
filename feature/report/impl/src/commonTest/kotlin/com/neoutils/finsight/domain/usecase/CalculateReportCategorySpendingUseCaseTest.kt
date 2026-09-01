package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.model.ExchangeRate
import com.neoutils.finsight.domain.model.MoneyByCurrency
import com.neoutils.finsight.domain.model.ReportPerspective
import com.neoutils.finsight.domain.model.SpendingSubject
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.domain.repository.AccountFlows
import com.neoutils.finsight.domain.repository.AssetMonthFlowsByCurrency
import com.neoutils.finsight.domain.repository.DimensionFlowsByCurrency
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.IBaseCurrencyRepository
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.repository.IExchangeRateRepository
import com.neoutils.finsight.domain.repository.LiabilityMonthFlowsByCurrency
import com.neoutils.finsight.domain.repository.ScopeStatsByCurrency
import com.neoutils.finsight.ui.icons.CategoryLazyIcon
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The report's own translation of the ledger's aggregate into the facade's vocabulary:
 * the `null` group becomes the unclassified subject, a dimension becomes its category,
 * and a dimension that resolves to nothing at all is dropped rather than washed into the
 * bucket (design D7).
 *
 * Both entry points — the perspective one and the sub-ledger one — go through the same
 * build, so the position of the unclassified line is asserted on each.
 */
class CalculateReportCategorySpendingUseCaseTest {

    private val start = LocalDate(2026, 3, 1)
    private val end = LocalDate(2026, 3, 31)

    private val food = category(1, Category.Type.EXPENSE, dimensionId = 10)
    private val transport = category(2, Category.Type.EXPENSE, dimensionId = 11)
    private val salary = category(3, Category.Type.INCOME, dimensionId = 20)

    private fun category(id: Long, type: Category.Type, dimensionId: Long) = Category(
        id = id,
        name = "cat$id",
        icon = CategoryLazyIcon("icon"),
        type = type,
        createdAt = 0,
        dimensionId = dimensionId,
    )

    private fun useCase(
        perspectiveTotals: Map<Long?, Double> = emptyMap(),
        scopeTotals: Map<Long?, Double> = emptyMap(),
        nominalType: AccountType = AccountType.EXPENSE,
        categories: List<Category> = listOf(food, transport, salary),
    ) = CalculateReportCategorySpendingUseCase(
        entryRepository = BreakdownEntryRepository(
            perspective = mapOf(nominalType to perspectiveTotals.money()),
            scope = mapOf(nominalType to scopeTotals.money()),
        ),
        categoryRepository = BreakdownCategoryRepository(categories),
        accountRepository = BreakdownAccountRepository(
            listOf(Account(id = 1, name = "Bank", type = AccountType.ASSET, currency = "BRL"))
        ),
        creditCardRepository = BreakdownCreditCardRepository(emptyList()),
        consolidateMoney = ConsolidateMoneyUseCase(
            baseCurrencyRepository = BreakdownBaseCurrency("BRL"),
            exchangeRateRepository = BreakdownRates(),
            getAccountCurrencies = BreakdownAccountCurrencies(),
        ),
    )

    private fun Map<Long?, Double>.money() = mapValues { MoneyByCurrency.of("BRL", it.value) }

    private val perspective = ReportPerspective.AccountPerspective(listOf(1))

    @Test
    fun `the null group becomes the unclassified subject, last in the list`() = runTest {
        val result = useCase(
            perspectiveTotals = mapOf(10L to 50.0, 11L to 25.0, null to 125.0),
        )(perspective, start, end)

        assertEquals(
            listOf(
                SpendingSubject.Categorized(food),
                SpendingSubject.Categorized(transport),
                SpendingSubject.Uncategorized,
            ),
            result.map { it.subject },
            "the unclassified line is last whatever its size",
        )
        assertEquals(62.5, result.last().percentage!!, absoluteTolerance = 0.01) // 125 / 200
    }

    @Test
    fun `the sub-ledger entry point orders it the same way`() = runTest {
        val result = useCase(
            scopeTotals = mapOf(10L to 50.0, null to 30.0),
        ).forDimensions(dimensionIds = listOf(77), on = end)

        assertEquals(
            listOf(SpendingSubject.Categorized(food), SpendingSubject.Uncategorized),
            result.map { it.subject },
        )
        assertEquals(30.0, result.last().amount.terms.single().value)
    }

    @Test
    fun `an orphan dimension is dropped rather than washed into the bucket`() = runTest {
        val result = useCase(
            perspectiveTotals = mapOf(10L to 50.0, 999L to 70.0, null to 50.0),
        )(perspective, start, end)

        assertEquals(
            listOf(SpendingSubject.Categorized(food), SpendingSubject.Uncategorized),
            result.map { it.subject },
        )
        assertEquals(50.0, result.last().amount.terms.single().value, "the orphan is nowhere in it")
        assertEquals(50.0, result.first().percentage, "and it is not in the denominator either")
    }

    @Test
    fun `a fully classified period has no unclassified line`() = runTest {
        val result = useCase(perspectiveTotals = mapOf(10L to 50.0, 11L to 50.0))(perspective, start, end)

        assertEquals(
            listOf(SpendingSubject.Categorized(food), SpendingSubject.Categorized(transport)),
            result.map { it.subject },
        )
        assertEquals(listOf(50.0, 50.0), result.map { it.percentage })
    }

    @Test
    fun `an unclassified total of zero produces no line`() = runTest {
        val result = useCase(perspectiveTotals = mapOf(10L to 50.0, null to 0.0))(perspective, start, end)

        assertEquals(listOf(SpendingSubject.Categorized(food)), result.map { it.subject })
    }

    @Test
    fun `income and expense do not mix their unclassified totals`() = runTest {
        val income = useCase(
            perspectiveTotals = mapOf(20L to -3_000.0, null to -1_000.0),
            nominalType = AccountType.INCOME,
        )(perspective, start, end, TransactionType.INCOME)

        assertEquals(
            listOf(SpendingSubject.Categorized(salary), SpendingSubject.Uncategorized),
            income.map { it.subject },
        )
        assertEquals(1_000.0, income.last().amount.terms.single().value, "read positive by its own sign")

        // The same repository answers nothing for the other nature, so the expense
        // breakdown of this period is empty rather than borrowing the income figures.
        val expense = useCase(
            perspectiveTotals = mapOf(20L to -3_000.0, null to -1_000.0),
            nominalType = AccountType.INCOME,
        )(perspective, start, end, TransactionType.EXPENSE)

        assertEquals(emptyList(), expense)
    }
}

private class BreakdownEntryRepository(
    private val perspective: Map<AccountType, Map<Long?, MoneyByCurrency>> = emptyMap(),
    private val scope: Map<AccountType, Map<Long?, MoneyByCurrency>> = emptyMap(),
) : IEntryRepository {
    override suspend fun totalsByDimensionByCurrency(
        nominalType: AccountType,
        startDate: LocalDate,
        endDate: LocalDate,
        siblingAccountIds: List<Long>,
    ): Map<Long?, MoneyByCurrency> = perspective[nominalType].orEmpty()

    override suspend fun totalsByDimensionInScopeByCurrency(
        nominalType: AccountType,
        scopeDimensionIds: List<Long>,
    ): Map<Long?, MoneyByCurrency> = scope[nominalType].orEmpty()

    override suspend fun getEntriesByTransaction(transactionId: Long): List<Entry> = throw NotImplementedError()
    override fun observeEntriesByTransaction(transactionId: Long): Flow<List<Entry>> = throw NotImplementedError()
    override fun observeLedgerChanges(): Flow<Unit> = throw NotImplementedError()
    override suspend fun hasEntries(accountId: Long): Boolean = throw NotImplementedError()
    override suspend fun hasEntriesForDimension(dimensionId: Long): Boolean = throw NotImplementedError()
    override suspend fun balance(accountId: Long): Double = throw NotImplementedError()
    override suspend fun accountFlows(month: YearMonth, accountId: Long, yieldDimensionId: Long?): AccountFlows = throw NotImplementedError()
    override suspend fun accountBalanceUpTo(accountId: Long, target: LocalDate): Double = throw NotImplementedError()
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
    override suspend fun totalsByDimensionInMonthByCurrency(month: YearMonth, nominalType: AccountType): Map<Long?, MoneyByCurrency> = throw NotImplementedError()
    override suspend fun scopeStatsByCurrency(scopeAccountIds: List<Long>, startDate: LocalDate, endDate: LocalDate): ScopeStatsByCurrency = throw NotImplementedError()
}

private class BreakdownCategoryRepository(private val categories: List<Category>) : ICategoryRepository {
    override suspend fun getAllCategories(): List<Category> = categories
    override suspend fun getAllCategoriesIncludingClosed(): List<Category> = categories
    override fun observeAllCategoriesIncludingClosed(): Flow<List<Category>> = flowOf(categories)
    override fun observeAllCategories(): Flow<List<Category>> = flowOf(categories)
    override fun observeCategoriesByType(type: Category.Type): Flow<List<Category>> = throw NotImplementedError()
    override suspend fun getCategoryById(id: Long): Category? = categories.firstOrNull { it.id == id }
    override suspend fun getCategoryBySystemKey(systemKey: String): Category? = null
    override suspend fun getCategoryByDimensionId(dimensionId: Long): Category? =
        categories.firstOrNull { it.dimensionId == dimensionId }
    override fun observeCategoryById(id: Long): Flow<Category?> = throw NotImplementedError()
    override suspend fun archive(id: Long) = Unit
    override suspend fun unarchive(id: Long) = Unit
    override suspend fun existsByName(name: String, ignoreId: Long): Boolean = false
    override suspend fun insert(category: Category) = throw NotImplementedError()
    override suspend fun insertAll(categories: List<Category>) = throw NotImplementedError()
    override suspend fun update(category: Category) = throw NotImplementedError()
    override suspend fun delete(category: Category) = throw NotImplementedError()
}

private class BreakdownAccountRepository(private val accounts: List<Account>) : IAccountRepository {
    override suspend fun getAllAccountsIncludingClosed(): List<Account> = accounts
    override fun observeAllAccounts(): Flow<List<Account>> = throw NotImplementedError()
    override suspend fun getAllAccounts(): List<Account> = accounts
    override fun observeAllAccountsIncludingClosed(): Flow<List<Account>> = throw NotImplementedError()
    override suspend fun getAllLedgerAccounts(): List<Account> = throw NotImplementedError()
    override fun observeAllLedgerAccounts(): Flow<List<Account>> = throw NotImplementedError()
    override suspend fun getAccountById(accountId: Long): Account? = accounts.firstOrNull { it.id == accountId }
    override fun observeAccountById(accountId: Long): Flow<Account?> = throw NotImplementedError()
    override suspend fun getDefaultAccount(): Account? = accounts.firstOrNull()
    override fun observeDefaultAccount(): Flow<Account?> = throw NotImplementedError()
    override suspend fun hasYieldingAccount(): Boolean = false
    override suspend fun getAccountCount(): Int = accounts.size
    override suspend fun insert(account: Account): Long = throw NotImplementedError()
    override suspend fun update(account: Account) = throw NotImplementedError()
    override suspend fun delete(account: Account) = throw NotImplementedError()
    override suspend fun reopen(accountId: Long) = throw NotImplementedError()
}

private class BreakdownCreditCardRepository(private val cards: List<CreditCard>) : ICreditCardRepository {
    override suspend fun getCreditCardById(creditCardId: Long): CreditCard? = cards.firstOrNull { it.id == creditCardId }
    override fun observeAllCreditCards(): Flow<List<CreditCard>> = throw NotImplementedError()
    override suspend fun getAllCreditCards(): List<CreditCard> = cards
    override suspend fun getAllCreditCardsIncludingClosed(): List<CreditCard> = cards
    override fun observeAllCreditCardsIncludingClosed(): Flow<List<CreditCard>> = throw NotImplementedError()
    override fun observeCreditCardById(creditCardId: Long): Flow<CreditCard?> = throw NotImplementedError()
    override suspend fun insert(creditCard: CreditCard, currency: String): Long = throw NotImplementedError()
    override suspend fun update(creditCard: CreditCard) = throw NotImplementedError()
    override suspend fun delete(creditCard: CreditCard) = throw NotImplementedError()
    override suspend fun unarchive(accountId: Long) = throw NotImplementedError()
    override suspend fun currencyForNewCard(): String = throw NotImplementedError()
}

private class BreakdownBaseCurrency(base: String) : IBaseCurrencyRepository {
    private val flow = MutableStateFlow(base)
    override fun observe(): StateFlow<String> = flow
    override suspend fun set(code: String) { flow.value = code }
}

private class BreakdownRates : IExchangeRateRepository {
    override suspend fun rateAsOf(currency: String, date: LocalDate): ExchangeRate? = null
    override suspend fun ratesAsOf(date: LocalDate): Map<String, ExchangeRate> = emptyMap()
    override suspend fun rateBetween(from: String, to: String, date: LocalDate): ExchangeRate? = null
    override fun observeAll(): Flow<List<ExchangeRate>> = flowOf(emptyList())
    override suspend fun save(rate: ExchangeRate) = Unit
    override suspend fun remove(rate: ExchangeRate) = Unit
    override suspend fun countNaming(currency: String) = 0
}

private class BreakdownAccountCurrencies : GetAccountCurrenciesUseCase {
    override suspend fun invoke() = AccountCurrencies(inUse = listOf("BRL"), ofDefaultAccount = "BRL")
}
