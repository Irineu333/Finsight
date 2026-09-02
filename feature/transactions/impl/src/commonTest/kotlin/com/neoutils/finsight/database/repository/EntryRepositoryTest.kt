package com.neoutils.finsight.database.repository

import com.neoutils.finsight.database.dao.CurrencyTotal
import com.neoutils.finsight.database.dao.EntryDao
import com.neoutils.finsight.database.entity.EntryEntity
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.MoneyByCurrency
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals

class EntryRepositoryTest {

    @Test
    fun `given account entries when balanceUpTo then cents are converted to reais`() = runTest {
        val repository = EntryRepository(FakeReadEntryDao(balanceUpTo = -12000))
        assertEquals(-120.0, repository.accountBalanceUpTo(accountId = 1, target = LocalDate(2026, 1, 31)))
    }

    @Test
    fun `given no account when balanceUpTo then it reads the ASSET nature by one path only`() = runTest {
        val repository = EntryRepository(FakeReadEntryDao(byType = mapOf("ASSET" to 8_200)))
        assertEquals(MoneyByCurrency.of("BRL", 82.0), repository.balanceUpToByCurrency(YearMonth(2026, 2)))
    }

    @Test
    fun `given liabilities when naturalBalanceUpTo then the same mechanism serves them`() = runTest {
        val repository = EntryRepository(FakeReadEntryDao(byType = mapOf("LIABILITY" to -4_500)))
        assertEquals(
            MoneyByCurrency.of("BRL", -45.0),
            repository.naturalBalanceUpToByCurrency(YearMonth(2026, 3), AccountType.LIABILITY),
        )
    }

    @Test
    fun `given two currencies when a nature is read then each stands on its own`() = runTest {
        // The conversion from cents happens per currency, and no currency is summed
        // with another on the way out of the repository.
        val repository = EntryRepository(
            FakeReadEntryDao(byType = mapOf("ASSET" to 8_200), byTypeUsd = 1_050),
        )

        val balances = repository.naturalBalanceUpToByCurrency(YearMonth(2026, 2), AccountType.ASSET)

        assertEquals(82.0, balances["BRL"])
        assertEquals(10.5, balances["USD"])
    }

    @Test
    fun `given a liability invoice balance when dimensionOwed then it reads positive`() = runTest {
        // A card purchase leaves the liability leg negative (-5000 cents); owed reads +50.
        val repository = EntryRepository(FakeReadEntryDao(invoice = -5000))
        assertEquals(MoneyByCurrency.of("BRL", 50.0), repository.dimensionOwedByCurrency(dimensionId = 7))
    }

}

/**
 * The grouped aggregates answer in [LEGACY_CURRENCY], plus a second currency where a
 * test asks for one — which is how a row list stands in for a `GROUP BY currency`.
 */
private const val LEGACY_CURRENCY = "BRL"

private class FakeReadEntryDao(
    private val balanceUpTo: Long = 0,
    private val byType: Map<String, Long> = emptyMap(),
    private val byTypeUsd: Long? = null,
    private val inMonth: Long = 0,
    private val invoice: Long = 0,
) : EntryDao {
    private fun rows(total: Long, usd: Long? = null) = listOfNotNull(
        CurrencyTotal(LEGACY_CURRENCY, total),
        usd?.let { CurrencyTotal("USD", it) },
    )

    override suspend fun balanceUpToDate(accountId: Long, date: String): Long = balanceUpTo
    override suspend fun balanceUpToMonthByType(
        type: String,
        yearMonth: String,
        excludedAccountIds: Collection<Long>,
    ): List<CurrencyTotal> = rows(byType.getValue(type), byTypeUsd)
    override suspend fun dimensionBalanceInMonth(dimensionId: Long, yearMonth: String): List<CurrencyTotal> = rows(inMonth)
    override suspend fun dimensionMonthlySeries(dimensionId: Long, untilYearMonth: String): List<com.neoutils.finsight.database.dao.MonthCurrencyTotal> = throw NotImplementedError()
    override suspend fun dimensionNaturalBalance(dimensionId: Long): List<CurrencyTotal> = rows(invoice)
    override suspend fun naturalBalanceByDimension(dimensionIds: List<Long>): List<com.neoutils.finsight.database.dao.DimensionCurrencyTotal> =
        dimensionIds.map { com.neoutils.finsight.database.dao.DimensionCurrencyTotal(it, LEGACY_CURRENCY, invoice) }
    override suspend fun periodTotalsByDimension(dimensionIds: List<Long>): List<com.neoutils.finsight.database.dao.DimensionPeriodTotalsRow> = throw NotImplementedError()
    override suspend fun netWorthCents(): List<CurrencyTotal> = throw NotImplementedError()
    override suspend fun insert(entry: EntryEntity): Long = throw NotImplementedError()
    override suspend fun insertAll(entries: List<EntryEntity>): List<Long> = throw NotImplementedError()
    override suspend fun deleteByTransactionId(transactionId: Long) = throw NotImplementedError()
    override suspend fun getAll(): List<EntryEntity> = throw NotImplementedError()
    override fun observeAll(): Flow<List<EntryEntity>> = throw NotImplementedError()
    override fun observeEntryCount(): Flow<Long> = flowOf(0)
    override suspend fun hasEntries(accountId: Long): Boolean = false
    override suspend fun hasEntriesForDimension(dimensionId: Long): Boolean = false
    override suspend fun getByTransactionIds(transactionIds: Collection<Long>): List<EntryEntity> =
        throw NotImplementedError()

    override suspend fun getByTransactionId(transactionId: Long): List<EntryEntity> = throw NotImplementedError()
    override suspend fun getEntriesWithAccountByTransactionId(transactionId: Long): List<com.neoutils.finsight.database.dao.EntryWithAccount> = throw NotImplementedError()
    override fun observeEntriesWithAccountByTransactionId(transactionId: Long): Flow<List<com.neoutils.finsight.database.dao.EntryWithAccount>> = throw NotImplementedError()
    override suspend fun accountPeriodTotals(accountId: Long, yearMonth: String, yieldDimensionId: Long?): com.neoutils.finsight.database.dao.AccountPeriodTotals = throw NotImplementedError()
    override suspend fun balanceOf(accountId: Long): Long = throw NotImplementedError()
    override suspend fun dimensionPeriodTotals(dimensionId: Long): List<com.neoutils.finsight.database.dao.DimensionPeriodTotals> = throw NotImplementedError()
    override suspend fun liabilityMonthTotals(yearMonth: String): List<com.neoutils.finsight.database.dao.LiabilityMonthTotals> = throw NotImplementedError()
    override suspend fun assetMonthTotals(yearMonth: String, yieldDimensionId: Long?): List<com.neoutils.finsight.database.dao.AssetMonthTotals> = throw NotImplementedError()
    override suspend fun totalsByDimensionWithSiblingLeg(
        categoryType: String,
        start: kotlinx.datetime.LocalDate,
        end: kotlinx.datetime.LocalDate,
        siblingAccountIds: List<Long>,
    ): List<com.neoutils.finsight.database.dao.DimensionCurrencyTotal> = throw NotImplementedError()
    override suspend fun totalsByDimensionInMonth(
        nominalType: String,
        yearMonth: String,
    ): List<com.neoutils.finsight.database.dao.DimensionCurrencyTotal> = throw NotImplementedError()
    override suspend fun totalsByDimensionInScope(
        nominalType: String,
        scopeDimensionIds: List<Long>,
    ): List<com.neoutils.finsight.database.dao.DimensionCurrencyTotal> = throw NotImplementedError()
    override suspend fun scopeStats(scopeIds: List<Long>, startDate: kotlinx.datetime.LocalDate, endDate: kotlinx.datetime.LocalDate): List<com.neoutils.finsight.database.dao.ScopeStatsTotals> = throw NotImplementedError()
}
