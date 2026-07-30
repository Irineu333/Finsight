package com.neoutils.finsight.test

import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.CurrencyBalance
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.repository.AccountBalance
import com.neoutils.finsight.domain.repository.AccountFlows
import com.neoutils.finsight.domain.repository.AssetMonthFlows
import com.neoutils.finsight.domain.repository.DimensionFlows
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.repository.LiabilityMonthFlows
import com.neoutils.finsight.domain.repository.ScopeStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth

/**
 * Every read of the ledger, refused — a base for this module's fakes, which override only
 * the reads their own test actually asserts.
 *
 * Refusing rather than answering zero is the point: a test that reaches a read nobody
 * stubbed fails loudly instead of asserting over a number the fake invented. Each fake used
 * to redeclare the whole interface to say exactly this, once per file, which meant every
 * change to the ledger's read surface was a sweep over every one of them.
 */
internal abstract class StubEntryRepository : IEntryRepository {

    override suspend fun getEntriesByTransaction(transactionId: Long): List<Entry> = notStubbed()

    override fun observeEntriesByTransaction(transactionId: Long): Flow<List<Entry>> = notStubbed()

    /**
     * The one default that answers, because it is plumbing and not a figure: a screen whose
     * numbers are SQL aggregates has nothing else to observe, so a fake that refused this
     * would leave every ViewModel test hanging on its first emission.
     */
    override fun observeLedgerChanges(): Flow<Unit> = flowOf(Unit)

    override suspend fun balanceUpTo(target: YearMonth, accountId: Long): AccountBalance = notStubbed()

    override suspend fun naturalBalanceUpTo(target: YearMonth, type: AccountType): CurrencyBalance = notStubbed()

    override suspend fun hasEntries(accountId: Long): Boolean = notStubbed()

    override suspend fun hasEntriesForDimension(dimensionId: Long): Boolean = notStubbed()

    override suspend fun balance(accountId: Long): AccountBalance = notStubbed()

    override suspend fun dimensionBalanceInMonth(month: YearMonth, dimensionId: Long): CurrencyBalance = notStubbed()

    override suspend fun accountFlows(month: YearMonth, accountId: Long): AccountFlows = notStubbed()

    override suspend fun dimensionEntryCountInMonth(month: YearMonth, dimensionId: Long): Int = notStubbed()

    override suspend fun dimensionOwed(dimensionId: Long): CurrencyBalance = notStubbed()

    override suspend fun dimensionFlows(dimensionId: Long): DimensionFlows = notStubbed()

    override suspend fun liabilityMonthFlows(month: YearMonth): LiabilityMonthFlows = notStubbed()

    override suspend fun assetMonthFlows(month: YearMonth): AssetMonthFlows = notStubbed()

    override suspend fun totalsByDimension(
        nominalType: AccountType,
        startDate: LocalDate,
        endDate: LocalDate,
        siblingAccountIds: List<Long>,
    ): Map<Long?, CurrencyBalance> = notStubbed()

    override suspend fun totalsByDimensionInScope(
        nominalType: AccountType,
        scopeDimensionIds: List<Long>,
    ): Map<Long?, CurrencyBalance> = notStubbed()

    override suspend fun scopeStats(
        scopeAccountIds: List<Long>,
        startDate: LocalDate,
        endDate: LocalDate,
    ): ScopeStats = notStubbed()
}

private fun notStubbed(): Nothing =
    throw NotImplementedError("This ledger read is not stubbed: the test asked for a figure it never set up")

/**
 * A per-currency figure of the one currency every expectation in these tests is written in.
 * Nothing here changes because a second currency became expressible: that is the point.
 */
internal fun brl(value: Double): CurrencyBalance = CurrencyBalance.of("BRL", value)

/** The same, for a figure scoped to a single account. */
internal fun brlBalance(value: Double): AccountBalance = AccountBalance("BRL", value)
