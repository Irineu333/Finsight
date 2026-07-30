package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.repository.AccountFlows
import com.neoutils.finsight.domain.repository.LiabilityMonthFlows
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.repository.DimensionFlows
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The delegation, and the **shape** of each half of it: one account answers a number,
 * every account answers per currency. That split is what makes the dashboard's total
 * the door multi-currency enters the app by, and it is the whole reason the two are
 * separate members rather than one nullable parameter (design D8).
 */
class CalculateBalanceUseCaseTest {

    private class FakeEntryRepository(
        private val byAccount: Map<Long, Double> = emptyMap(),
        private val spanning: Map<String, Double> = emptyMap(),
    ) : IEntryRepository {
        override suspend fun accountBalanceUpTo(accountId: Long, target: YearMonth): Double =
            byAccount.getValue(accountId)

        override suspend fun balanceUpToByCurrency(target: YearMonth) =
            com.neoutils.finsight.domain.model.MoneyByCurrency.of(spanning)

        override suspend fun balanceUpTo(target: YearMonth, accountId: Long?): Double = throw NotImplementedError()
        override suspend fun naturalBalanceUpTo(target: YearMonth, type: com.neoutils.finsight.domain.model.AccountType): Double = throw NotImplementedError()
        override suspend fun getEntriesByTransaction(transactionId: Long): List<Entry> = throw NotImplementedError()
        override fun observeEntriesByTransaction(transactionId: Long): Flow<List<Entry>> = throw NotImplementedError()
        override fun observeLedgerChanges(): Flow<Unit> = flowOf(Unit)
        override suspend fun balance(accountId: Long): Double = throw NotImplementedError()
        override suspend fun hasEntries(accountId: Long): Boolean = false
        override suspend fun hasEntriesForDimension(dimensionId: Long): Boolean = false
        override suspend fun dimensionBalanceInMonth(month: YearMonth, dimensionId: Long): Double = throw NotImplementedError()
        override suspend fun accountFlows(month: YearMonth, accountId: Long): AccountFlows = throw NotImplementedError()
        override suspend fun dimensionEntryCountInMonth(month: YearMonth, dimensionId: Long): Int = throw NotImplementedError()
        override suspend fun dimensionOwed(dimensionId: Long): Double = throw NotImplementedError()
        override suspend fun dimensionFlows(dimensionId: Long): DimensionFlows = throw NotImplementedError()
        override suspend fun liabilityMonthFlows(month: YearMonth): LiabilityMonthFlows = throw NotImplementedError()
        override suspend fun assetMonthFlows(month: YearMonth): com.neoutils.finsight.domain.repository.AssetMonthFlows = throw NotImplementedError()
        override suspend fun totalsByDimension(nominalType: AccountType, startDate: LocalDate, endDate: LocalDate, siblingAccountIds: List<Long>): Map<Long?, Double> = throw NotImplementedError()
        override suspend fun totalsByDimensionInScope(nominalType: AccountType, scopeDimensionIds: List<Long>): Map<Long?, Double> = throw NotImplementedError()
        override suspend fun scopeStats(scopeAccountIds: List<Long>, startDate: LocalDate, endDate: LocalDate): com.neoutils.finsight.domain.repository.ScopeStats = throw NotImplementedError()
    }

    @Test
    fun `one account answers a number`() = runTest {
        val useCase = CalculateBalanceUseCase(FakeEntryRepository(byAccount = mapOf(1L to 110.0)))

        assertEquals(110.0, useCase.forAccount(accountId = 1, target = YearMonth(2026, 3)))
    }

    @Test
    fun `every account answers per currency, and one currency is still per currency`() = runTest {
        val useCase = CalculateBalanceUseCase(FakeEntryRepository(spanning = mapOf("BRL" to 130.0)))

        val balance = useCase(target = YearMonth(2026, 3))

        assertEquals(130.0, balance["BRL"])
        assertEquals(setOf("BRL"), balance.currencies)
    }

    /**
     * The case the shape exists for. Nothing here adds 130 to 40: the two are two facts,
     * and reducing them to one is conversion, which happens above the ledger.
     */
    @Test
    fun `two currencies come back as two`() = runTest {
        val useCase = CalculateBalanceUseCase(
            FakeEntryRepository(spanning = mapOf("BRL" to 130.0, "USD" to 40.0)),
        )

        val balance = useCase(target = YearMonth(2026, 3))

        assertEquals(130.0, balance["BRL"])
        assertEquals(40.0, balance["USD"])
    }
}
