package com.neoutils.finsight.database.repository

import com.neoutils.finsight.database.dao.AccountPeriodTotals
import com.neoutils.finsight.database.dao.AssetMonthTotals
import com.neoutils.finsight.database.dao.CurrencyTotal
import com.neoutils.finsight.database.dao.DimensionPeriodTotals
import com.neoutils.finsight.database.dao.DimensionPeriodTotalsRow
import com.neoutils.finsight.database.dao.DimensionTotal
import com.neoutils.finsight.database.dao.EntryDao
import com.neoutils.finsight.database.dao.EntryWithAccount
import com.neoutils.finsight.database.dao.LiabilityMonthTotals
import com.neoutils.finsight.database.dao.ScopeStatsTotals
import com.neoutils.finsight.database.entity.EntryEntity
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.CurrencyBalance
import com.neoutils.finsight.domain.repository.AccountBalance
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EntryRepositoryTest {

    @Test
    fun `given account entries when balanceUpTo then cents are converted to reais`() = runTest {
        val repository = EntryRepository(FakeReadEntryDao(balanceUpTo = -12000))
        assertEquals(
            AccountBalance("BRL", -120.0),
            repository.balanceUpTo(YearMonth(2026, 1), accountId = 1),
        )
    }

    @Test
    fun `given a nature when naturalBalanceUpTo then each currency keeps its own figure`() = runTest {
        val repository = EntryRepository(
            FakeReadEntryDao(byType = mapOf("ASSET" to listOf(CurrencyTotal("BRL", 8_200), CurrencyTotal("USD", 4_000))))
        )

        assertEquals(
            CurrencyBalance.of(mapOf("BRL" to 82.0, "USD" to 40.0)),
            repository.naturalBalanceUpTo(YearMonth(2026, 2), AccountType.ASSET),
        )
    }

    @Test
    fun `given liabilities when naturalBalanceUpTo then the same mechanism serves them`() = runTest {
        val repository = EntryRepository(
            FakeReadEntryDao(byType = mapOf("LIABILITY" to listOf(CurrencyTotal("BRL", -4_500))))
        )

        assertEquals(
            CurrencyBalance.of("BRL", -45.0),
            repository.naturalBalanceUpTo(YearMonth(2026, 3), AccountType.LIABILITY),
        )
    }

    @Test
    fun `given a liability invoice balance when dimensionOwed then it reads positive`() = runTest {
        // A card purchase leaves the liability leg negative (-5000 cents); owed reads +50.
        val repository = EntryRepository(FakeReadEntryDao(invoice = -5000))

        assertEquals(CurrencyBalance.of("BRL", 50.0), repository.dimensionOwed(dimensionId = 7))
    }

    @Test
    fun `given an account outside the chart when balance then the read refuses to answer`() = runTest {
        // The figure is denominated by the account; with no account there is no currency for
        // it, and a `0.0` would be a number in nobody's currency.
        val repository = EntryRepository(FakeReadEntryDao(accountInChart = false))

        assertFailsWith<IllegalArgumentException> { repository.balance(accountId = 42) }
    }
}

private class FakeReadEntryDao(
    private val balanceUpTo: Long = 0,
    private val byType: Map<String, List<CurrencyTotal>> = emptyMap(),
    private val inMonth: Long = 0,
    private val invoice: Long = 0,
    private val accountInChart: Boolean = true,
) : EntryDao {
    private fun accountRow(total: Long) = CurrencyTotal("BRL", total).takeIf { accountInChart }

    override suspend fun balanceUpToMonth(accountId: Long, yearMonth: String) = accountRow(balanceUpTo)
    override suspend fun balanceUpToMonthByType(type: String, yearMonth: String) = byType.getValue(type)
    override suspend fun dimensionBalanceInMonth(dimensionId: Long, yearMonth: String) =
        listOf(CurrencyTotal("BRL", inMonth))
    override suspend fun dimensionNaturalBalance(dimensionId: Long) = listOf(CurrencyTotal("BRL", invoice))
    override suspend fun naturalBalanceByDimension(dimensionIds: List<Long>): List<DimensionTotal> =
        dimensionIds.map { DimensionTotal(it, "BRL", invoice) }
    override suspend fun periodTotalsByDimension(dimensionIds: List<Long>): List<DimensionPeriodTotalsRow> =
        throw NotImplementedError()
    override suspend fun netWorthCents(): List<CurrencyTotal> = throw NotImplementedError()
    override suspend fun insert(entry: EntryEntity): Long = throw NotImplementedError()
    override suspend fun insertAll(entries: List<EntryEntity>): List<Long> = throw NotImplementedError()
    override suspend fun deleteByTransactionId(transactionId: Long) = throw NotImplementedError()
    override suspend fun getAll(): List<EntryEntity> = throw NotImplementedError()
    override fun observeAll(): Flow<List<EntryEntity>> = throw NotImplementedError()
    override fun observeEntryCount(): Flow<Long> = flowOf(0)
    override suspend fun hasEntries(accountId: Long): Boolean = false
    override suspend fun hasEntriesForDimension(dimensionId: Long): Boolean = false
    override suspend fun getByTransactionId(transactionId: Long): List<EntryEntity> = throw NotImplementedError()
    override suspend fun getEntriesWithAccountByTransactionId(transactionId: Long): List<EntryWithAccount> =
        throw NotImplementedError()
    override fun observeEntriesWithAccountByTransactionId(transactionId: Long): Flow<List<EntryWithAccount>> =
        throw NotImplementedError()
    override suspend fun accountPeriodTotals(accountId: Long, yearMonth: String): AccountPeriodTotals? =
        throw NotImplementedError()
    override suspend fun dimensionEntryCountInMonth(dimensionId: Long, yearMonth: String): Int =
        throw NotImplementedError()
    override suspend fun balanceOf(accountId: Long) = accountRow(balanceUpTo)
    override suspend fun dimensionPeriodTotals(dimensionId: Long): List<DimensionPeriodTotals> =
        throw NotImplementedError()
    override suspend fun liabilityMonthTotals(yearMonth: String): List<LiabilityMonthTotals> =
        throw NotImplementedError()
    override suspend fun assetMonthTotals(yearMonth: String): List<AssetMonthTotals> = throw NotImplementedError()
    override suspend fun totalsByDimensionWithSiblingLeg(
        nominalType: String,
        start: LocalDate,
        end: LocalDate,
        siblingAccountIds: List<Long>,
    ): List<DimensionTotal> = throw NotImplementedError()
    override suspend fun totalsByDimensionInScope(
        nominalType: String,
        scopeDimensionIds: List<Long>,
    ): List<DimensionTotal> = throw NotImplementedError()
    override suspend fun scopeStats(
        scopeIds: List<Long>,
        startDate: LocalDate,
        endDate: LocalDate,
    ): List<ScopeStatsTotals> = throw NotImplementedError()
}
