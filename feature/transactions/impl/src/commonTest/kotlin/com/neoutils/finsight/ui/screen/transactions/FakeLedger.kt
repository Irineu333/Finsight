@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.ui.screen.transactions

import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.CurrencyBalance
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.model.Transaction
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
import kotlinx.datetime.yearMonth
import kotlin.time.ExperimentalTime

/**
 * A ledger in memory over a list of transactions, classifying entries by the same rules
 * the SQL aggregates use. The screen's summary must satisfy its identity against a real
 * ledger, not against numbers handed to it: a fake that simply echoes constants would
 * let `closing = opening + Σ flows` pass while the composition underneath was wrong.
 *
 * The queries themselves are verified against Room in `core/ledger`'s jvm tests.
 */
internal class FakeLedger(private val transactions: List<Transaction>) : IEntryRepository {

    private fun Transaction.hasEquityLeg() = entries.any { it.account.type == AccountType.EQUITY }

    private fun Transaction.hasNominalLeg() = entries.any {
        it.account.type == AccountType.EXPENSE || it.account.type == AccountType.INCOME
    }

    private fun legsOf(type: AccountType, month: YearMonth): List<Pair<Transaction, Entry>> =
        transactions.filter { it.date.yearMonth == month }
            .flatMap { transaction ->
                transaction.entries.filter { it.account.type == type }.map { transaction to it }
            }

    override suspend fun naturalBalanceUpTo(target: YearMonth, type: AccountType): CurrencyBalance =
        transactions.filter { it.date.yearMonth <= target }
            .flatMap { it.entries }
            .filter { it.account.type == type }
            .perCurrency { it.amount }

    override suspend fun balanceUpTo(target: YearMonth, accountId: Long): AccountBalance {
        val legs = transactions.filter { it.date.yearMonth <= target }
            .flatMap { it.entries }
            .filter { it.account.id == accountId }
        // One account, one currency: the fake reads it off the account, as the query does.
        return AccountBalance(
            currency = legs.firstOrNull()?.account?.currency ?: "BRL",
            amount = legs.sumOf { it.amount } / 100.0,
        )
    }

    /** Grouped by currency, because that is the shape every cross-account read has. */
    private fun List<Entry>.perCurrency(cents: (Entry) -> Long): CurrencyBalance = CurrencyBalance.of(
        groupBy { it.currency }.mapValues { (_, legs) -> legs.sumOf(cents) / 100.0 }
    )

    /** The same, for legs still paired with the transaction that classifies them. */
    private fun List<Pair<Transaction, Entry>>.perCurrency(
        cents: (Transaction, Entry) -> Long,
    ): CurrencyBalance = CurrencyBalance.of(
        groupBy { (_, entry) -> entry.currency }
            .mapValues { (_, rows) -> rows.sumOf { (transaction, entry) -> cents(transaction, entry) } / 100.0 }
    )

    /**
     * Only transactions with a nominal or equity counter-leg count — which is exactly
     * "not a transfer and not a card payment", the two forms whose money never leaves
     * the user's own accounts.
     */
    override suspend fun assetMonthFlows(month: YearMonth): AssetMonthFlows {
        val legs = legsOf(AccountType.ASSET, month)
            .filter { (transaction, _) -> transaction.hasEquityLeg() || transaction.hasNominalLeg() }

        return AssetMonthFlows(
            income = legs.perCurrency { t, e -> if (!t.hasEquityLeg() && e.amount > 0) e.amount else 0L },
            expense = legs.perCurrency { t, e -> if (!t.hasEquityLeg() && e.amount < 0) -e.amount else 0L },
            adjustment = legs.perCurrency { t, e -> if (t.hasEquityLeg()) e.amount else 0L },
        )
    }

    override suspend fun liabilityMonthFlows(month: YearMonth): LiabilityMonthFlows {
        val legs = legsOf(AccountType.LIABILITY, month)

        return LiabilityMonthFlows(
            expense = legs.perCurrency { t, e -> if (!t.hasEquityLeg() && e.amount < 0) -e.amount else 0L },
            payment = legs.perCurrency { t, e -> if (!t.hasEquityLeg() && e.amount > 0) e.amount else 0L },
            adjustment = legs.perCurrency { t, e -> if (t.hasEquityLeg()) e.amount else 0L },
        )
    }

    override fun observeLedgerChanges(): Flow<Unit> = flowOf(Unit)
    override suspend fun hasEntries(accountId: Long): Boolean = false
    override suspend fun hasEntriesForDimension(dimensionId: Long): Boolean = false
    override suspend fun getEntriesByTransaction(transactionId: Long): List<Entry> = throw NotImplementedError()
    override fun observeEntriesByTransaction(transactionId: Long): Flow<List<Entry>> = throw NotImplementedError()
    override suspend fun balance(accountId: Long): AccountBalance = throw NotImplementedError()
    override suspend fun dimensionBalanceInMonth(month: YearMonth, dimensionId: Long): CurrencyBalance =
        throw NotImplementedError()
    override suspend fun accountFlows(month: YearMonth, accountId: Long): AccountFlows = throw NotImplementedError()
    override suspend fun dimensionEntryCountInMonth(month: YearMonth, dimensionId: Long): Int = throw NotImplementedError()
    override suspend fun dimensionOwed(dimensionId: Long): CurrencyBalance = throw NotImplementedError()
    override suspend fun dimensionFlows(dimensionId: Long): DimensionFlows = throw NotImplementedError()
    override suspend fun totalsByDimension(nominalType: AccountType, startDate: LocalDate, endDate: LocalDate, siblingAccountIds: List<Long>): Map<Long?, CurrencyBalance> = throw NotImplementedError()
    override suspend fun totalsByDimensionInScope(nominalType: AccountType, scopeDimensionIds: List<Long>): Map<Long?, CurrencyBalance> = throw NotImplementedError()
    override suspend fun scopeStats(scopeAccountIds: List<Long>, startDate: LocalDate, endDate: LocalDate): ScopeStats = throw NotImplementedError()
}
