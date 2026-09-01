@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.ui.screen.transactions

import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.model.MoneyByCurrency
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.ExchangeRate
import com.neoutils.finsight.domain.repository.AccountFlows
import com.neoutils.finsight.domain.repository.IBaseCurrencyRepository
import com.neoutils.finsight.domain.repository.IExchangeRateRepository
import com.neoutils.finsight.domain.usecase.AccountCurrencies
import com.neoutils.finsight.domain.usecase.ConsolidateMoneyUseCase
import com.neoutils.finsight.domain.usecase.GetAccountCurrenciesUseCase
import com.neoutils.finsight.domain.usecase.ObserveConsolidationChangesUseCase
import com.neoutils.finsight.extension.ConsolidatedAmount
import com.neoutils.finsight.extension.DisplayAmount
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.neoutils.finsight.domain.repository.AssetMonthFlowsByCurrency
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.repository.LiabilityMonthFlowsByCurrency
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.yearMonth
import kotlin.time.ExperimentalTime
import com.neoutils.finsight.domain.repository.DimensionFlowsByCurrency
import com.neoutils.finsight.domain.repository.ScopeStatsByCurrency

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

    /** Cents grouped by the currency of the account the leg posted to (design D5). */
    private fun List<Entry>.byCurrency(): MoneyByCurrency = MoneyByCurrency.of(
        groupBy { it.currency }.mapValues { (_, legs) -> legs.sumOf { it.amount } / 100.0 },
    )

    override suspend fun naturalBalanceUpToByCurrency(
        target: YearMonth,
        type: AccountType,
        excludedAccountIds: Set<Long>,
    ) = transactions.filter { it.date.yearMonth <= target }
        .flatMap { it.entries }
        .filter { it.account.type == type && it.account.id !in excludedAccountIds }
        .byCurrency()

    override suspend fun balanceUpToByCurrency(target: YearMonth, excludedAccountIds: Set<Long>) =
        naturalBalanceUpToByCurrency(target, AccountType.ASSET, excludedAccountIds)

    override suspend fun accountBalanceUpTo(accountId: Long, target: LocalDate): Double =
        transactions.filter { it.date <= target }
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

    override suspend fun assetMonthFlowsByCurrency(
        month: YearMonth,
        yieldDimensionId: Long?,
    ): AssetMonthFlowsByCurrency {
        val income = Bucket()
        val yield = Bucket()
        val expense = Bucket()
        val adjustment = Bucket()

        legsOf(AccountType.ASSET, month).forEach { (transaction, entry) ->
            if (!transaction.hasEquityLeg() && !transaction.hasNominalLeg()) return@forEach
            when {
                transaction.hasEquityLeg() -> adjustment.add(entry, entry.amount)
                // The yield line takes exactly what the income line gives up, so the
                // two together are what income alone was.
                entry.amount > 0 && transaction.isYield(yieldDimensionId) ->
                    yield.add(entry, entry.amount)
                entry.amount > 0 -> income.add(entry, entry.amount)
                entry.amount < 0 -> expense.add(entry, -entry.amount)
            }
        }

        return AssetMonthFlowsByCurrency(
            income.money(),
            yield.money(),
            expense.money(),
            adjustment.money(),
        )
    }

    override suspend fun liabilityMonthFlowsByCurrency(month: YearMonth): LiabilityMonthFlowsByCurrency {
        val expense = Bucket()
        val payment = Bucket()
        val adjustment = Bucket()

        legsOf(AccountType.LIABILITY, month).forEach { (transaction, entry) ->
            when {
                transaction.hasEquityLeg() -> adjustment.add(entry, entry.amount)
                entry.amount < 0 -> expense.add(entry, -entry.amount)
                entry.amount > 0 -> payment.add(entry, entry.amount)
            }
        }

        return LiabilityMonthFlowsByCurrency(expense.money(), payment.money(), adjustment.money())
    }

    /** Cents accumulated per currency, the way a `GROUP BY e.currency` accumulates them. */
    private class Bucket {
        private val cents = mutableMapOf<String, Long>()
        fun add(entry: Entry, amount: Long) {
            cents[entry.currency] = (cents[entry.currency] ?: 0L) + amount
        }
        fun money() = MoneyByCurrency.of(cents.mapValues { it.value / 100.0 })
    }

    override fun observeLedgerChanges(): Flow<Unit> = flowOf(Unit)
    override suspend fun hasEntries(accountId: Long): Boolean = false
    override suspend fun hasEntriesForDimension(dimensionId: Long): Boolean = false
    override suspend fun getEntriesByTransaction(transactionId: Long): List<Entry> = throw NotImplementedError()
    override fun observeEntriesByTransaction(transactionId: Long): Flow<List<Entry>> = throw NotImplementedError()
    override suspend fun balance(accountId: Long): Double = throw NotImplementedError()
    override suspend fun accountFlows(month: YearMonth, accountId: Long, yieldDimensionId: Long?): AccountFlows = throw NotImplementedError()

    override suspend fun dimensionMonthlySeriesByCurrency(dimensionId: Long, upTo: YearMonth): Map<YearMonth, MoneyByCurrency> = throw NotImplementedError()
    override suspend fun dimensionBalanceInMonthByCurrency(month: YearMonth, dimensionId: Long): MoneyByCurrency = throw NotImplementedError()
    override suspend fun dimensionOwedByCurrency(dimensionId: Long): MoneyByCurrency = throw NotImplementedError()
    override suspend fun dimensionFlowsByCurrency(dimensionId: Long): DimensionFlowsByCurrency = throw NotImplementedError()
    override suspend fun owedByDimensionByCurrency(dimensionIds: Collection<Long>): Map<Long, MoneyByCurrency> = throw NotImplementedError()
    override suspend fun flowsByDimensionByCurrency(dimensionIds: Collection<Long>): Map<Long, DimensionFlowsByCurrency> = throw NotImplementedError()
    override suspend fun totalsByDimensionByCurrency(
        nominalType: AccountType,
        startDate: LocalDate,
        endDate: LocalDate,
        siblingAccountIds: List<Long>,
    ): Map<Long?, MoneyByCurrency> = throw NotImplementedError()
    override suspend fun totalsByDimensionInMonthByCurrency(
        month: YearMonth,
        nominalType: AccountType,
    ): Map<Long?, MoneyByCurrency> = throw NotImplementedError()
    override suspend fun totalsByDimensionInScopeByCurrency(
        nominalType: AccountType,
        scopeDimensionIds: List<Long>,
    ): Map<Long?, MoneyByCurrency> = throw NotImplementedError()
    override suspend fun scopeStatsByCurrency(
        scopeAccountIds: List<Long>,
        startDate: LocalDate,
        endDate: LocalDate,
    ): ScopeStatsByCurrency = throw NotImplementedError()
}

/** The base currency in force. One currency, which is all the app has until group 12. */
internal class FakeBaseCurrency(code: String = "BRL") : IBaseCurrencyRepository {
    private val state = MutableStateFlow(code)
    override fun observe(): StateFlow<String> = state
    override suspend fun set(code: String) { state.value = code }
}

/** An empty archive: with a single currency in play, no rate is ever consulted. */
internal object NoExchangeRates : IExchangeRateRepository {
    override suspend fun rateAsOf(currency: String, date: LocalDate): ExchangeRate? = null
    override suspend fun ratesAsOf(date: LocalDate): Map<String, ExchangeRate> = emptyMap()
    override suspend fun rateBetween(from: String, to: String, date: LocalDate): ExchangeRate? = null
    override fun observeAll(): Flow<List<ExchangeRate>> = flowOf(emptyList())
    override suspend fun save(rate: ExchangeRate) = throw NotImplementedError()
    override suspend fun remove(rate: ExchangeRate) = throw NotImplementedError()
    override suspend fun countNaming(currency: String) = 0
}

/**
 * The real reducer over the fakes, never a stub: what the summary asserts is the figure
 * a user reads, and a fake reducer would let a wrong policy or a lost term pass.
 */
internal fun consolidator(baseCurrency: String = "BRL") = ConsolidateMoneyUseCase(
    baseCurrencyRepository = FakeBaseCurrency(baseCurrency),
    exchangeRateRepository = NoExchangeRates,
    getAccountCurrencies = FakeAccountCurrencies(baseCurrency),
)

internal class FakeAccountCurrencies(
    private vararg val inUse: String,
) : GetAccountCurrenciesUseCase {
    override suspend fun invoke() =
        AccountCurrencies(inUse = inUse.toList(), ofDefaultAccount = inUse.firstOrNull())
}

/**
 * The composed invalidation trigger over the same fakes — what a view model listens to
 * so that a rate registered elsewhere reaches a figure that was already on screen.
 */
internal fun FakeLedger.consolidationChanges(baseCurrency: String = "BRL") =
    ObserveConsolidationChangesUseCase(
        entryRepository = this,
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
