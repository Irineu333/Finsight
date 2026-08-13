package com.neoutils.finsight.ui.screen.dashboard

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.CategorySpending
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.MoneyByCurrency
import com.neoutils.finsight.domain.repository.AccountFlows
import com.neoutils.finsight.domain.repository.DimensionFlowsByCurrency
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.repository.ScopeStatsByCurrency
import com.neoutils.finsight.domain.usecase.CalculateBalanceUseCase
import com.neoutils.finsight.domain.usecase.CalculateBudgetProgressUseCase
import com.neoutils.finsight.domain.usecase.CalculateCategoryIncomeUseCase
import com.neoutils.finsight.domain.usecase.CalculateCategorySpendingUseCase
import com.neoutils.finsight.domain.usecase.GetPendingRecurringUseCase
import com.neoutils.finsight.feature.shell.api.NavCatalog
import com.neoutils.finsight.feature.shell.api.NavDestination
import com.neoutils.finsight.ui.mapper.InvoiceUiMapper
import com.neoutils.finsight.ui.model.InvoiceUi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * The perimeter of the total-balance widget: which accounts make up its figure.
 *
 * The set the user authored has to reach the **read**, not a sum done here — so what
 * these tests pin is that the widget hands its excluded ids down and shows whatever
 * came back, including when nothing did.
 */
class DashboardTotalBalancePerimeterTest {

    /** Balances the ledger answers per account, so the excluded set is visible in the total. */
    private val perAccount = mapOf(1L to 70.0, 2L to 30.0)

    private val ledger = PerimeterEntryRepository(perAccount)

    private fun builder() = DashboardComponentsBuilder(
        calculateBalanceUseCase = CalculateBalanceUseCase(entryRepository = ledger),
        calculateCategorySpendingUseCase = object : CalculateCategorySpendingUseCase {
            override suspend fun invoke(forYearMonth: YearMonth): List<CategorySpending> = throw NotImplementedError()
        },
        calculateCategoryIncomeUseCase = object : CalculateCategoryIncomeUseCase {
            override suspend fun invoke(forYearMonth: YearMonth): List<CategorySpending> = throw NotImplementedError()
        },
        calculateBudgetProgressUseCase = CalculateBudgetProgressUseCase(ledger, reducer()),
        getPendingRecurringUseCase = GetPendingRecurringUseCase(),
        invoiceUiMapper = object : InvoiceUiMapper {
            override suspend fun toUi(invoice: Invoice, cardInvoices: List<Invoice>): InvoiceUi =
                throw NotImplementedError()
        },
        entryRepository = ledger,
        accountRepository = FakeAccountRepository(),
        consolidateMoney = reducer(),
        navCatalog = object : NavCatalog { override val destinations: List<NavDestination> = emptyList() },
    )

    private suspend fun totalBalance(config: Map<String, String>): DashboardComponent.TotalBalance? =
        builder().build(
            key = DashboardComponentType.TOTAL_BALANCE.key,
            input = DashboardComponentsInput(
                transactions = emptyList(),
                creditCards = emptyList(),
                invoicesByCreditCardId = emptyMap(),
                accounts = emptyList(),
                budgets = emptyList(),
                recurringList = emptyList(),
                occurrences = emptyList(),
                today = LocalDate(2026, 3, 20),
                targetMonth = YearMonth(2026, 3),
            ),
            context = DashboardBuilderContext(pendingRecurring = emptyList()),
            config = config,
        ) as DashboardComponent.TotalBalance?

    @Test
    fun `a dashboard with no exclusion configured shows what it always showed`() = runTest {
        assertEquals(100.0, totalBalance(config = emptyMap())?.amount?.value)
        assertEquals(emptySet(), ledger.lastExcluded)
    }

    @Test
    fun `the widget's own default is the empty set`() = runTest {
        val default = DashboardComponentType.TOTAL_BALANCE.defaultConfig

        assertEquals(
            emptySet(),
            default.excludedIds(TotalBalanceConfig.EXCLUDED_ACCOUNT_IDS),
        )
    }

    @Test
    fun `an excluded account leaves the total`() = runTest {
        val total = totalBalance(config = mapOf(TotalBalanceConfig.EXCLUDED_ACCOUNT_IDS to "2"))

        assertEquals(70.0, total?.amount?.value)
        // The perimeter travelled as ids into the read; nothing was summed here.
        assertEquals(setOf(2L), ledger.lastExcluded)
    }

    @Test
    fun `reincluding an account gives the figure back`() = runTest {
        totalBalance(config = mapOf(TotalBalanceConfig.EXCLUDED_ACCOUNT_IDS to "2"))

        assertEquals(100.0, totalBalance(config = mapOf(TotalBalanceConfig.EXCLUDED_ACCOUNT_IDS to ""))?.amount?.value)
    }

    @Test
    fun `excluding every account shows zero rather than hiding the widget`() = runTest {
        val total = totalBalance(config = mapOf(TotalBalanceConfig.EXCLUDED_ACCOUNT_IDS to "1,2"))

        assertNotNull(total)
        assertEquals(0.0, total.amount.value)
    }

    @Test
    fun `an id matching no account changes nothing`() = runTest {
        assertEquals(
            100.0,
            totalBalance(config = mapOf(TotalBalanceConfig.EXCLUDED_ACCOUNT_IDS to "99"))?.amount?.value,
        )
    }
}

/**
 * Sums the per-account balances the perimeter left in, grouped by currency — the shape
 * the real read has, so the widget is exercised against a figure and not a scalar. It
 * records the set it was given: the assertion that the exclusion reached the read at all.
 */
private class PerimeterEntryRepository(
    private val perAccount: Map<Long, Double>,
) : IEntryRepository {

    var lastExcluded: Set<Long>? = null
        private set

    override suspend fun balanceUpToByCurrency(
        target: YearMonth,
        excludedAccountIds: Set<Long>,
    ): MoneyByCurrency {
        lastExcluded = excludedAccountIds
        val remaining = perAccount.filterKeys { it !in excludedAccountIds }
        // No account left is no row at all, exactly as a grouped aggregate answers —
        // the empty figure the consolidation layer turns into a denominated zero.
        return if (remaining.isEmpty()) {
            MoneyByCurrency.zero
        } else {
            MoneyByCurrency.of("BRL", remaining.values.sum())
        }
    }

    override suspend fun getEntriesByTransaction(transactionId: Long): List<Entry> = throw NotImplementedError()
    override fun observeEntriesByTransaction(transactionId: Long): Flow<List<Entry>> = throw NotImplementedError()
    override fun observeLedgerChanges(): Flow<Unit> = flowOf(Unit)
    override suspend fun balance(accountId: Long): Double = throw NotImplementedError()
    override suspend fun hasEntries(accountId: Long): Boolean = false
    override suspend fun hasEntriesForDimension(dimensionId: Long): Boolean = false
    override suspend fun accountFlows(month: YearMonth, accountId: Long, yieldDimensionId: Long?): AccountFlows = throw NotImplementedError()
    override suspend fun dimensionEntryCountInMonth(month: YearMonth, dimensionId: Long): Int = throw NotImplementedError()
    override suspend fun liabilityMonthFlowsByCurrency(month: YearMonth) = throw NotImplementedError()
    override suspend fun assetMonthFlowsByCurrency(month: YearMonth, yieldDimensionId: Long?) = throw NotImplementedError()
    override suspend fun accountBalanceUpTo(accountId: Long, target: LocalDate): Double = throw NotImplementedError()
    override suspend fun naturalBalanceUpToByCurrency(target: YearMonth, type: AccountType, excludedAccountIds: Set<Long>): MoneyByCurrency = throw NotImplementedError()
    override suspend fun dimensionBalanceInMonthByCurrency(month: YearMonth, dimensionId: Long): MoneyByCurrency = throw NotImplementedError()
    override suspend fun dimensionOwedByCurrency(dimensionId: Long): MoneyByCurrency = throw NotImplementedError()
    override suspend fun dimensionFlowsByCurrency(dimensionId: Long): DimensionFlowsByCurrency = throw NotImplementedError()
    override suspend fun owedByDimensionByCurrency(dimensionIds: Collection<Long>): Map<Long, MoneyByCurrency> = throw NotImplementedError()
    override suspend fun flowsByDimensionByCurrency(dimensionIds: Collection<Long>): Map<Long, DimensionFlowsByCurrency> = throw NotImplementedError()
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
