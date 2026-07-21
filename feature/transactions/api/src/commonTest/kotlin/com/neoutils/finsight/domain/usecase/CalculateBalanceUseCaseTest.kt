package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.repository.AccountFlows
import com.neoutils.finsight.domain.repository.CardMonthFlows
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.repository.InvoiceFlows
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Task 4.3 removed the in-memory (CAP-2) form; only the ledger-backed form remains,
 * a thin delegation to [IEntryRepository.balanceUpTo] (the figure itself is proven by
 * the SQL-level EntryRepository/DB tests). This pins the delegation: one account, and
 * all accounts (null id).
 */
class CalculateBalanceUseCaseTest {

    private class FakeEntryRepository(private val byAccount: Map<Long?, Double>) : IEntryRepository {
        override suspend fun balanceUpTo(target: YearMonth, accountId: Long?): Double = byAccount.getValue(accountId)
        override suspend fun getEntriesByTransaction(transactionId: Long): List<Entry> = throw NotImplementedError()
        override fun observeEntriesByTransaction(transactionId: Long): Flow<List<Entry>> = throw NotImplementedError()
        override fun observeLedgerChanges(): Flow<Unit> = flowOf(Unit)
        override suspend fun balance(accountId: Long): Double = throw NotImplementedError()
        override suspend fun hasEntries(accountId: Long): Boolean = false
        override suspend fun balanceInMonth(month: YearMonth, accountId: Long): Double = throw NotImplementedError()
        override suspend fun accountFlows(month: YearMonth, accountId: Long): AccountFlows = throw NotImplementedError()
        override suspend fun entryCountInMonth(month: YearMonth, accountId: Long): Int = throw NotImplementedError()
        override suspend fun invoiceOwed(invoiceId: Long): Double = throw NotImplementedError()
        override suspend fun invoiceFlows(invoiceId: Long): InvoiceFlows = throw NotImplementedError()
        override suspend fun cardMonthFlows(month: YearMonth): CardMonthFlows = throw NotImplementedError()
        override suspend fun netWorth(): Double = throw NotImplementedError()
        override suspend fun categoryTotals(categoryType: AccountType, startDate: LocalDate, endDate: LocalDate, siblingAccountIds: List<Long>): Map<Long, Double> = throw NotImplementedError()
        override suspend fun categoryTotalsForInvoices(categoryType: AccountType, invoiceIds: List<Long>): Map<Long, Double> = throw NotImplementedError()
        override suspend fun reportStats(scopeAccountIds: List<Long>, startDate: LocalDate, endDate: LocalDate): com.neoutils.finsight.domain.repository.ReportStats = throw NotImplementedError()
    }

    @Test
    fun `delegates to the ledger balanceUpTo`() = runTest {
        val useCase = CalculateBalanceUseCase(FakeEntryRepository(mapOf(1L to 110.0, null to 130.0)))

        assertEquals(110.0, useCase(target = YearMonth(2026, 3), accountId = 1))
        assertEquals(130.0, useCase(target = YearMonth(2026, 3)))
    }
}
