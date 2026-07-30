package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.CurrencyBalance
import com.neoutils.finsight.domain.repository.IEntryRepository
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth

/**
 * Every read of the ledger refused, so a case declares the one it depends on by overriding it.
 *
 * Refusing rather than answering zero is the point: a use case that grew a dependency it did
 * not declare fails loudly here instead of quietly reading a plausible nothing. It is the
 * per-module base of task 3.7 — shared inside this module, and not across the graph.
 */
internal abstract class StubEntryRepository : IEntryRepository {
    override suspend fun dimensionBalanceInMonth(month: YearMonth, dimensionId: Long): CurrencyBalance =
        throw NotImplementedError()

    override suspend fun getEntriesByTransaction(transactionId: Long) = throw NotImplementedError()
    override fun observeEntriesByTransaction(transactionId: Long) = throw NotImplementedError()
    override fun observeLedgerChanges() = throw NotImplementedError()
    override suspend fun balanceUpTo(target: YearMonth, accountId: Long) = throw NotImplementedError()
    override suspend fun naturalBalanceUpTo(target: YearMonth, type: AccountType): CurrencyBalance =
        throw NotImplementedError()
    override suspend fun hasEntries(accountId: Long) = throw NotImplementedError()
    override suspend fun hasEntriesForDimension(dimensionId: Long) = throw NotImplementedError()
    override suspend fun balance(accountId: Long) = throw NotImplementedError()
    override suspend fun accountFlows(month: YearMonth, accountId: Long) = throw NotImplementedError()
    override suspend fun dimensionEntryCountInMonth(month: YearMonth, dimensionId: Long) = throw NotImplementedError()
    override suspend fun dimensionOwed(dimensionId: Long) = throw NotImplementedError()
    override suspend fun dimensionFlows(dimensionId: Long) = throw NotImplementedError()
    override suspend fun liabilityMonthFlows(month: YearMonth) = throw NotImplementedError()
    override suspend fun assetMonthFlows(month: YearMonth) = throw NotImplementedError()
    override suspend fun totalsByDimension(
        nominalType: AccountType,
        startDate: LocalDate,
        endDate: LocalDate,
        siblingAccountIds: List<Long>,
    ) = throw NotImplementedError()
    override suspend fun totalsByDimensionInScope(
        nominalType: AccountType,
        scopeDimensionIds: List<Long>,
    ) = throw NotImplementedError()
    override suspend fun scopeStats(
        scopeAccountIds: List<Long>,
        startDate: LocalDate,
        endDate: LocalDate,
    ) = throw NotImplementedError()
}
