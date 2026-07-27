@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.ui.screen.transactions

import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.model.Transaction
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

    override suspend fun naturalBalanceUpTo(target: YearMonth, type: AccountType): Double =
        transactions.filter { it.date.yearMonth <= target }
            .flatMap { it.entries }
            .filter { it.account.type == type }
            .sumOf { it.amount } / 100.0

    override suspend fun balanceUpTo(target: YearMonth, accountId: Long?): Double =
        if (accountId == null) naturalBalanceUpTo(target, AccountType.ASSET)
        else transactions.filter { it.date.yearMonth <= target }
            .flatMap { it.entries }
            .filter { it.account.id == accountId }
            .sumOf { it.amount } / 100.0

    /**
     * Only transactions with a nominal or equity counter-leg count — which is exactly
     * "not a transfer and not a card payment", the two forms whose money never leaves
     * the user's own accounts.
     */
    /** Whether the income counter-leg of this transaction carries [dimensionId]. */
    private fun Transaction.isYield(dimensionId: Long?) = dimensionId != null && entries.any {
        it.account.type == AccountType.INCOME && it.dimensionId == dimensionId
    }

    override suspend fun assetMonthFlows(month: YearMonth, yieldDimensionId: Long?): AssetMonthFlows {
        var income = 0L
        var yield = 0L
        var expense = 0L
        var adjustment = 0L

        legsOf(AccountType.ASSET, month).forEach { (transaction, entry) ->
            if (!transaction.hasEquityLeg() && !transaction.hasNominalLeg()) return@forEach
            when {
                transaction.hasEquityLeg() -> adjustment += entry.amount
                // The yield line takes exactly what the income line gives up, so the
                // two together are what income alone was.
                entry.amount > 0 && transaction.isYield(yieldDimensionId) -> yield += entry.amount
                entry.amount > 0 -> income += entry.amount
                entry.amount < 0 -> expense += -entry.amount
            }
        }

        return AssetMonthFlows(income / 100.0, yield / 100.0, expense / 100.0, adjustment / 100.0)
    }

    override suspend fun liabilityMonthFlows(month: YearMonth): LiabilityMonthFlows {
        var expense = 0L
        var payment = 0L
        var adjustment = 0L

        legsOf(AccountType.LIABILITY, month).forEach { (transaction, entry) ->
            when {
                transaction.hasEquityLeg() -> adjustment += entry.amount
                entry.amount < 0 -> expense += -entry.amount
                entry.amount > 0 -> payment += entry.amount
            }
        }

        return LiabilityMonthFlows(expense / 100.0, payment / 100.0, adjustment / 100.0)
    }

    override fun observeLedgerChanges(): Flow<Unit> = flowOf(Unit)
    override suspend fun hasEntries(accountId: Long): Boolean = false
    override suspend fun hasEntriesForDimension(dimensionId: Long): Boolean = false
    override suspend fun getEntriesByTransaction(transactionId: Long): List<Entry> = throw NotImplementedError()
    override fun observeEntriesByTransaction(transactionId: Long): Flow<List<Entry>> = throw NotImplementedError()
    override suspend fun balance(accountId: Long): Double = throw NotImplementedError()
    override suspend fun dimensionBalanceInMonth(month: YearMonth, dimensionId: Long): Double = throw NotImplementedError()
    override suspend fun accountFlows(month: YearMonth, accountId: Long, yieldDimensionId: Long?): AccountFlows = throw NotImplementedError()
    override suspend fun dimensionEntryCountInMonth(month: YearMonth, dimensionId: Long): Int = throw NotImplementedError()
    override suspend fun dimensionOwed(dimensionId: Long): Double = throw NotImplementedError()
    override suspend fun dimensionFlows(dimensionId: Long): DimensionFlows = throw NotImplementedError()
    override suspend fun netWorth(): Double = throw NotImplementedError()
    override suspend fun totalsByDimension(nominalType: AccountType, startDate: LocalDate, endDate: LocalDate, siblingAccountIds: List<Long>): Map<Long?, Double> = throw NotImplementedError()
    override suspend fun totalsByDimensionInScope(nominalType: AccountType, scopeDimensionIds: List<Long>): Map<Long?, Double> = throw NotImplementedError()
    override suspend fun scopeStats(scopeAccountIds: List<Long>, startDate: LocalDate, endDate: LocalDate): ScopeStats = throw NotImplementedError()
}

/** No account declares a yield, so the summary offers no yield line. */
internal object FakeAccountsForYield : com.neoutils.finsight.domain.repository.IAccountRepository {
    override suspend fun hasYieldingAccount(): Boolean = false
    override fun observeHasYieldingAccount(): Flow<Boolean> = flowOf(false)
    override fun observeAllAccounts(): Flow<List<com.neoutils.finsight.domain.model.Account>> = flowOf(emptyList())
    override suspend fun getAllAccounts(): List<com.neoutils.finsight.domain.model.Account> = emptyList()
    override suspend fun getAllAccountsIncludingClosed(): List<com.neoutils.finsight.domain.model.Account> = emptyList()
    override fun observeAllAccountsIncludingClosed(): Flow<List<com.neoutils.finsight.domain.model.Account>> = flowOf(emptyList())
    override suspend fun getAllLedgerAccounts(): List<com.neoutils.finsight.domain.model.Account> = emptyList()
    override fun observeAllLedgerAccounts(): Flow<List<com.neoutils.finsight.domain.model.Account>> = flowOf(emptyList())
    override suspend fun getAccountById(accountId: Long): com.neoutils.finsight.domain.model.Account? = null
    override fun observeAccountById(accountId: Long): Flow<com.neoutils.finsight.domain.model.Account?> = flowOf(null)
    override suspend fun getDefaultAccount(): com.neoutils.finsight.domain.model.Account? = null
    override fun observeDefaultAccount(): Flow<com.neoutils.finsight.domain.model.Account?> = flowOf(null)
    override suspend fun getAccountCount(): Int = 0
    override suspend fun insert(account: com.neoutils.finsight.domain.model.Account): Long = throw NotImplementedError()
    override suspend fun update(account: com.neoutils.finsight.domain.model.Account) = throw NotImplementedError()
    override suspend fun delete(account: com.neoutils.finsight.domain.model.Account) = throw NotImplementedError()
    override suspend fun reopen(accountId: Long) = throw NotImplementedError()
}
