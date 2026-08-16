package com.neoutils.finsight.domain.repository

import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.model.MoneyByCurrency
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth

/**
 * Reads derived from the double-entry ledger. Every figure is a sum of entry
 * amounts (debit-positive), so account balance, category spending, invoice owed
 * and net worth all share one mechanism — no per-type sign rule anywhere.
 * Values are returned in the major currency unit.
 *
 * **A read that can span accounts is expressed per currency.** The criterion is
 * single and not a list: every aggregate that does not filter by one account
 * returns a [MoneyByCurrency]. Only the reads scoped to a single account stay
 * scalar, because there the currency is an attribute of the account. Reducing a
 * multi-currency figure to one number is conversion, and conversion lives above
 * the ledger (design D8).
 *
 * A read scoped to a **dimension** is expressed per currency too, whatever the
 * dimension's kind. This interface MUST NOT consult `DimensionKind` to decide the
 * shape of a return: nothing in the ledger ties a dimension to a single account,
 * and that an invoice's dimension always falls on one card is a guarantee of the
 * *card facade*, not a construction of the ledger. A feature that knows its figure
 * holds one currency reduces it itself, where that guarantee is written.
 */
/**
 * The per-account, per-period money flows an account screen shows, derived from the
 * ledger, denominated in [currency] — the currency of the account itself, since this
 * read is scoped to one. [adjustment] is signed; the rest are positive magnitudes.
 *
 * [yield] repartitions [income]: it is the slice classified by the dimension the
 * caller asked to separate, and [income] no longer contains it. Without the
 * dimension it is zero and [income] is what it always was.
 */
data class AccountFlows(
    val currency: String,
    val income: Double,
    val yield: Double,
    val expense: Double,
    val adjustment: Double,
    val settlement: Double,
)

/** [DimensionFlows] expressed per currency — the shape a dimension's flows really have. */
data class DimensionFlowsByCurrency(
    val expense: MoneyByCurrency,
    val advancePayment: MoneyByCurrency,
    val adjustment: MoneyByCurrency,
) {
    companion object {
        val zero = DimensionFlowsByCurrency(
            expense = MoneyByCurrency.zero,
            advancePayment = MoneyByCurrency.zero,
            adjustment = MoneyByCurrency.zero,
        )
    }
}

/** [LiabilityMonthFlows] expressed per currency. */
data class LiabilityMonthFlowsByCurrency(
    val expense: MoneyByCurrency,
    val payment: MoneyByCurrency,
    val adjustment: MoneyByCurrency,
) {
    companion object {
        val zero = LiabilityMonthFlowsByCurrency(
            expense = MoneyByCurrency.zero,
            payment = MoneyByCurrency.zero,
            adjustment = MoneyByCurrency.zero,
        )
    }
}

/**
 * The month-wide income/expense/adjustment across every ASSET account, per currency,
 * derived from the ledger. Transfers and card payments are excluded — neither is
 * income or expense. [income]/[expense] are positive magnitudes; [adjustment] is signed.
 * [yield] repartitions [income], exactly as in [AccountFlows].
 */
data class AssetMonthFlowsByCurrency(
    val income: MoneyByCurrency,
    val yield: MoneyByCurrency,
    val expense: MoneyByCurrency,
    val adjustment: MoneyByCurrency,
) {
    companion object {
        val zero = AssetMonthFlowsByCurrency(
            income = MoneyByCurrency.zero,
            yield = MoneyByCurrency.zero,
            expense = MoneyByCurrency.zero,
            adjustment = MoneyByCurrency.zero,
        )
    }
}

/** [ScopeStats] expressed per currency. */
data class ScopeStatsByCurrency(
    val income: MoneyByCurrency,
    val expense: MoneyByCurrency,
    val balance: MoneyByCurrency,
    val openingBalance: MoneyByCurrency,
) {
    companion object {
        val zero = ScopeStatsByCurrency(
            income = MoneyByCurrency.zero,
            expense = MoneyByCurrency.zero,
            balance = MoneyByCurrency.zero,
            openingBalance = MoneyByCurrency.zero,
        )
    }
}

interface IEntryRepository {

    /** The entries (legs) of a transaction, each hydrated with its account. */
    suspend fun getEntriesByTransaction(transactionId: Long): List<Entry>

    /** Observes the entries (legs) of a transaction, each hydrated with its account. */
    fun observeEntriesByTransaction(transactionId: Long): Flow<List<Entry>>

    /**
     * Emits whenever the ledger changes. A screen whose numbers come from the SQL
     * aggregates below has nothing else to observe — the aggregates are `suspend`,
     * so without this its balances stay frozen while the ledger moves underneath.
     */
    fun observeLedgerChanges(): Flow<Unit>

    /**
     * Natural balance of [accountId] up to and including [target]. Scalar, and it
     * stays scalar: one account is one currency, which the caller already knows.
     *
     * The cut is by **day**, the resolution the transaction date already carries. This
     * is the real read; the monthly form below derives from it.
     *
     * The per-currency reads that follow keep their monthly cut — the asymmetry is
     * deliberate, and stays until a consumer of theirs asks by day.
     */
    suspend fun accountBalanceUpTo(accountId: Long, target: LocalDate): Double

    /**
     * The same balance asked with less precision: up to the last day of [target].
     * Not another number, so not another implementation.
     */
    suspend fun accountBalanceUpTo(accountId: Long, target: YearMonth): Double =
        accountBalanceUpTo(accountId = accountId, target = target.lastDay)

    /**
     * Natural balance of every ASSET account up to and including [target], per
     * currency — the read the dashboard's total balance comes through, and therefore
     * the door multi-currency enters the app by.
     */
    suspend fun balanceUpToByCurrency(
        target: YearMonth,
        excludedAccountIds: Set<Long> = emptySet(),
    ): MoneyByCurrency

    /**
     * Natural balance of every account of [type] up to and including [target], per
     * currency — the accumulated-balance read expressed by nature of account rather
     * than with one nature fixed in its own text. The consolidated figure of two
     * natures is their **sum** (`MoneyByCurrency.plus`), since liabilities are stored
     * in credit; there is no second aggregate for it and no sign rule of its own.
     *
     * [excludedAccountIds] leaves those accounts out of the sum. They are **identities
     * of accounts in the chart** — entities of the ledger — and nothing else: why a
     * caller wants a narrower perimeter is not expressible here and is not the ledger's
     * business. The empty set is the default and means the whole nature. An id matching
     * no account excludes nothing, which needs no code of its own.
     */
    suspend fun naturalBalanceUpToByCurrency(
        target: YearMonth,
        type: AccountType,
        excludedAccountIds: Set<Long> = emptySet(),
    ): MoneyByCurrency

    /**
     * Whether [accountId] has any movement. The fact behind "can this be removed
     * or only closed" — the decision itself belongs to `ArchiveAccountUseCase`.
     */
    suspend fun hasEntries(accountId: Long): Boolean

    /**
     * The same fact, for a facade keyed by dimension rather than by account — a
     * category. Same mechanism, different key; without it the delete-vs-archive
     * gate would simply disappear for categories.
     */
    suspend fun hasEntriesForDimension(dimensionId: Long): Boolean

    /** All-time natural balance of [accountId], across every date. Scoped to one account, so scalar. */
    suspend fun balance(accountId: Long): Double

    /** Natural balance of [dimensionId] within [month], per currency — category spending. */
    suspend fun dimensionBalanceInMonthByCurrency(
        month: YearMonth,
        dimensionId: Long,
    ): MoneyByCurrency

    /**
     * The income/expense/adjustment/invoice-payment flows of [accountId] in [month].
     *
     * [yieldDimensionId] is the dimension whose income is to be reported on its own
     * line instead of inside `income`. The ledger takes an identity and nothing more:
     * translating a facade into it belongs to whoever owns the facade. Omit it and
     * the breakdown is the undivided one.
     */
    suspend fun accountFlows(
        month: YearMonth,
        accountId: Long,
        yieldDimensionId: Long? = null,
    ): AccountFlows

    /** Number of ledger entries carrying [dimensionId] within [month]. */
    suspend fun dimensionEntryCountInMonth(month: YearMonth, dimensionId: Long): Int

    /** Amount owed on a sub-ledger (positive), per currency, from the entries carrying its dimension. */
    suspend fun dimensionOwedByCurrency(dimensionId: Long): MoneyByCurrency

    /** The expense/advance-payment/adjustment breakdown of a sub-ledger, per currency. */
    suspend fun dimensionFlowsByCurrency(dimensionId: Long): DimensionFlowsByCurrency

    /**
     * The owed total of each sub-ledger in [dimensionIds], per currency, keyed by
     * dimension — the batched [dimensionOwedByCurrency] a screen listing many invoices
     * needs. A dimension with no entries is absent from the map (its owed is 0), and
     * N invoices cost one read, not N.
     */
    suspend fun owedByDimensionByCurrency(
        dimensionIds: Collection<Long>,
    ): Map<Long, MoneyByCurrency>

    /**
     * The flows breakdown of each sub-ledger in [dimensionIds], per currency — the
     * batched [dimensionFlowsByCurrency]. Same one-query-not-N contract as
     * [owedByDimensionByCurrency].
     */
    suspend fun flowsByDimensionByCurrency(
        dimensionIds: Collection<Long>,
    ): Map<Long, DimensionFlowsByCurrency>

    /** Month-wide card expense/payment across every card account, per currency. */
    suspend fun liabilityMonthFlowsByCurrency(month: YearMonth): LiabilityMonthFlowsByCurrency

    /**
     * The month-wide income/expense/adjustment across every ASSET account, per
     * currency, excluding transfers and card payments — the summary a transaction
     * list or dashboard shows.
     *
     * [yieldDimensionId] separates a dimension's income onto its own line, exactly as
     * in [accountFlows] and with the same degradation when absent. It repartitions
     * each currency's income independently, because the dimension is one and the
     * currencies it lands in are whatever the accounts declare.
     */
    suspend fun assetMonthFlowsByCurrency(
        month: YearMonth,
        yieldDimensionId: Long? = null,
    ): AssetMonthFlowsByCurrency

    /**
     * Net worth per currency: `Σ ASSET + Σ LIABILITY`, over every date, with the
     * conversion accounts left out.
     *
     * **It is a sum and not a subtraction.** A liability is stored in credit, so the
     * debt is already negative and adding the two natures is what "assets minus what
     * is owed" means in the ledger's own sign. There is no sign rule of its own here,
     * exactly as in [naturalBalanceUpToByCurrency].
     *
     * **CONVERSION stays out, and that is the point.** With the rate at 5.50, a
     * transfer of R$ 550 → US$ 100 leaves `−550 BRL` and `+100 USD` in the user's own
     * accounts, which consolidate to zero: net worth does not move, as it should.
     * Including the conversion accounts would count the exchange result twice.
     *
     * It is a **different read** from [balanceUpToByCurrency], which answers over ASSET
     * alone and up to a target month — the figure the dashboard shows. The two are
     * different numbers about the same money, and a consumer that confused them would
     * report card debt as if it were not owed.
     */
    suspend fun netWorthByCurrency(): MoneyByCurrency

    /**
     * Natural balance per dimension of the [nominalType] legs in a date range,
     * counting only transactions that also have a leg on one of
     * [siblingAccountIds] — i.e. spending/income "seen from" those accounts.
     *
     * The `null` key is the unclassified total: legs on a nominal account carrying
     * no dimension. It is a group of the same aggregate, not a separate read.
     */
    suspend fun totalsByDimensionByCurrency(
        nominalType: AccountType,
        startDate: LocalDate,
        endDate: LocalDate,
        siblingAccountIds: List<Long>,
    ): Map<Long?, MoneyByCurrency>

    /**
     * Natural balance per dimension of the [nominalType] legs within [month], per
     * currency — a whole month's breakdown in one read.
     *
     * The `null` key is a group of this same aggregate: the nominal legs carrying no
     * dimension. It is not a separate read, so it can never diverge from the rest.
     *
     * The nature filter is part of the signature, not an optional clause: without it
     * the absence of a dimension would reach asset, liability and conversion legs
     * alike, and the null group would stop being a total about classification.
     */
    suspend fun totalsByDimensionInMonthByCurrency(
        month: YearMonth,
        nominalType: AccountType,
    ): Map<Long?, MoneyByCurrency>

    /** The same totals, scoped to the transactions touching a set of sub-ledgers. */
    suspend fun totalsByDimensionInScopeByCurrency(
        nominalType: AccountType,
        scopeDimensionIds: List<Long>,
    ): Map<Long?, MoneyByCurrency>

    /**
     * The income/expense/balance/opening-balance a report shows for an account or card
     * scope, over [startDate]..[endDate], derived from the ledger. [scopeAccountIds] are
     * the accounts the report is seen from (a perspective's ASSET accounts, or a card's
     * LIABILITY account); internal transfers among them are excluded. Empty scope yields
     * [ScopeStatsByCurrency.zero] — figures about nothing, in no currency; the caller
     * resolves "all accounts" before calling.
     */
    suspend fun scopeStatsByCurrency(
        scopeAccountIds: List<Long>,
        startDate: LocalDate,
        endDate: LocalDate,
    ): ScopeStatsByCurrency
}

/**
 * Natural balance of each dimension in [dimensionIds] within [month], per currency. A
 * thin fan over [IEntryRepository.dimensionBalanceInMonthByCurrency] so callers in
 * different feature `impl`s share one way to gather per-dimension month balances.
 */
suspend fun IEntryRepository.dimensionBalancesInMonthByCurrency(
    month: YearMonth,
    dimensionIds: Collection<Long>,
): Map<Long, MoneyByCurrency> =
    dimensionIds.distinct().associateWith { dimensionBalanceInMonthByCurrency(month, it) }
