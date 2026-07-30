package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.CurrencyBalance
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.repository.AccountBalance
import com.neoutils.finsight.domain.repository.AccountFlows
import com.neoutils.finsight.domain.repository.DimensionFlows
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.repository.LiabilityMonthFlows
import com.neoutils.finsight.domain.repository.ScopeStats
import com.neoutils.finsight.domain.repository.AssetMonthFlows
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Task 4.3 removed the in-memory (CAP-2) form; only the ledger-backed form remains, a thin
 * delegation to the ledger (the figures themselves are proven by the SQL-level
 * EntryRepository/DB tests). This pins the delegation, and the fact that the two forms are
 * now **different reads**: one account gives a number in that account's currency, while
 * every ASSET account gives a figure per currency, because it spans them.
 */
class CalculateBalanceUseCaseTest {

    private class FakeEntryRepository(
        private val account: AccountBalance,
        private val everyAsset: CurrencyBalance,
    ) : IEntryRepository {
        override suspend fun balanceUpTo(target: YearMonth, accountId: Long): AccountBalance = account
        override suspend fun naturalBalanceUpTo(target: YearMonth, type: AccountType): CurrencyBalance = everyAsset
        override suspend fun getEntriesByTransaction(transactionId: Long): List<Entry> = throw NotImplementedError()
        override fun observeEntriesByTransaction(transactionId: Long): Flow<List<Entry>> = throw NotImplementedError()
        override fun observeLedgerChanges(): Flow<Unit> = flowOf(Unit)
        override suspend fun balance(accountId: Long): AccountBalance = throw NotImplementedError()
        override suspend fun hasEntries(accountId: Long): Boolean = false
        override suspend fun hasEntriesForDimension(dimensionId: Long): Boolean = false
        override suspend fun dimensionBalanceInMonth(month: YearMonth, dimensionId: Long): CurrencyBalance =
            throw NotImplementedError()
        override suspend fun accountFlows(month: YearMonth, accountId: Long): AccountFlows = throw NotImplementedError()
        override suspend fun dimensionEntryCountInMonth(month: YearMonth, dimensionId: Long): Int =
            throw NotImplementedError()
        override suspend fun dimensionOwed(dimensionId: Long): CurrencyBalance = throw NotImplementedError()
        override suspend fun dimensionFlows(dimensionId: Long): DimensionFlows = throw NotImplementedError()
        override suspend fun liabilityMonthFlows(month: YearMonth): LiabilityMonthFlows = throw NotImplementedError()
        override suspend fun assetMonthFlows(month: YearMonth): AssetMonthFlows = throw NotImplementedError()
        override suspend fun totalsByDimension(
            nominalType: AccountType,
            startDate: LocalDate,
            endDate: LocalDate,
            siblingAccountIds: List<Long>,
        ): Map<Long?, CurrencyBalance> = throw NotImplementedError()
        override suspend fun totalsByDimensionInScope(
            nominalType: AccountType,
            scopeDimensionIds: List<Long>,
        ): Map<Long?, CurrencyBalance> = throw NotImplementedError()
        override suspend fun scopeStats(
            scopeAccountIds: List<Long>,
            startDate: LocalDate,
            endDate: LocalDate,
        ): ScopeStats = throw NotImplementedError()
    }

    private val ledger = FakeEntryRepository(
        account = AccountBalance("BRL", 110.0),
        everyAsset = CurrencyBalance.of(mapOf("BRL" to 130.0, "USD" to 40.0)),
    )

    @Test
    fun `naming an account gives one figure, in that account's own currency`() = runTest {
        val useCase = CalculateBalanceUseCase(ledger)

        assertEquals(AccountBalance("BRL", 110.0), useCase(target = YearMonth(2026, 3), accountId = 1))
    }

    @Test
    fun `naming none spans every account, so the figure stays per currency`() = runTest {
        val useCase = CalculateBalanceUseCase(ledger)

        // Not 170.0 of anything: reducing the two needs a rate, and this door is where
        // multi-currency enters the app.
        assertEquals(CurrencyBalance.of(mapOf("BRL" to 130.0, "USD" to 40.0)), useCase(target = YearMonth(2026, 3)))
    }
}
