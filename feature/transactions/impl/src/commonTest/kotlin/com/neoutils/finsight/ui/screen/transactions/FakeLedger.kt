@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.ui.screen.transactions

import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.ExchangeRate
import com.neoutils.finsight.domain.repository.AccountFlows
import com.neoutils.finsight.domain.repository.IBaseCurrencyRepository
import com.neoutils.finsight.domain.repository.IExchangeRateRepository
import com.neoutils.finsight.domain.usecase.ConsolidateMoneyUseCase
import com.neoutils.finsight.extension.ConsolidatedAmount
import com.neoutils.finsight.extension.DisplayAmount
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
    override suspend fun assetMonthFlows(month: YearMonth): AssetMonthFlows {
        var income = 0L
        var expense = 0L
        var adjustment = 0L

        legsOf(AccountType.ASSET, month).forEach { (transaction, entry) ->
            if (!transaction.hasEquityLeg() && !transaction.hasNominalLeg()) return@forEach
            when {
                transaction.hasEquityLeg() -> adjustment += entry.amount
                entry.amount > 0 -> income += entry.amount
                entry.amount < 0 -> expense += -entry.amount
            }
        }

        return AssetMonthFlows(income / 100.0, expense / 100.0, adjustment / 100.0)
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
    override suspend fun accountFlows(month: YearMonth, accountId: Long): AccountFlows = throw NotImplementedError()
    override suspend fun dimensionEntryCountInMonth(month: YearMonth, dimensionId: Long): Int = throw NotImplementedError()
    override suspend fun dimensionOwed(dimensionId: Long): Double = throw NotImplementedError()
    override suspend fun dimensionFlows(dimensionId: Long): DimensionFlows = throw NotImplementedError()
    override suspend fun totalsByDimension(nominalType: AccountType, startDate: LocalDate, endDate: LocalDate, siblingAccountIds: List<Long>): Map<Long?, Double> = throw NotImplementedError()
    override suspend fun totalsByDimensionInScope(nominalType: AccountType, scopeDimensionIds: List<Long>): Map<Long?, Double> = throw NotImplementedError()
    override suspend fun scopeStats(scopeAccountIds: List<Long>, startDate: LocalDate, endDate: LocalDate): ScopeStats = throw NotImplementedError()
}

/** The base currency in force. One currency, which is all the app has until group 12. */
internal class FakeBaseCurrency(private val code: String = "BRL") : IBaseCurrencyRepository {
    override fun observe(): StateFlow<String> = MutableStateFlow(code)
    override suspend fun set(currency: String) = throw NotImplementedError()
}

/** An empty archive: with a single currency in play, no rate is ever consulted. */
internal object NoExchangeRates : IExchangeRateRepository {
    override suspend fun rateAsOf(currency: String, date: LocalDate): ExchangeRate? = null
    override suspend fun ratesAsOf(date: LocalDate): Map<String, ExchangeRate> = emptyMap()
    override fun observeAll(): Flow<List<ExchangeRate>> = flowOf(emptyList())
    override suspend fun save(rate: ExchangeRate) = throw NotImplementedError()
    override suspend fun remove(rate: ExchangeRate) = throw NotImplementedError()
}

/**
 * The real reducer over the fakes, never a stub: what the summary asserts is the figure
 * a user reads, and a fake reducer would let a wrong policy or a lost term pass.
 */
internal fun consolidator(baseCurrency: String = "BRL") = ConsolidateMoneyUseCase(
    baseCurrencyRepository = FakeBaseCurrency(baseCurrency),
    exchangeRateRepository = NoExchangeRates,
)

/**
 * The single term of a summary figure. Every figure here is mono-currency by
 * construction — one currency goes into the reducer — so asking for `single()` is also
 * the assertion that nothing split it.
 */
internal val ConsolidatedAmount.term: DisplayAmount get() = terms.single()
internal val ConsolidatedAmount.value: Double get() = term.value
internal val ConsolidatedAmount.policy: DisplayAmount.SignPolicy get() = term.policy
