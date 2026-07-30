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
 */
data class AccountFlows(
    val currency: String,
    val income: Double,
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

/** [AssetMonthFlows] expressed per currency. */
data class AssetMonthFlowsByCurrency(
    val income: MoneyByCurrency,
    val expense: MoneyByCurrency,
    val adjustment: MoneyByCurrency,
) {
    companion object {
        val zero = AssetMonthFlowsByCurrency(
            income = MoneyByCurrency.zero,
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
     */
    suspend fun accountBalanceUpTo(accountId: Long, target: YearMonth): Double

    /**
     * Natural balance of every ASSET account up to and including [target], per
     * currency — the read the dashboard's total balance comes through, and therefore
     * the door multi-currency enters the app by.
     */
    suspend fun balanceUpToByCurrency(target: YearMonth): MoneyByCurrency

    /**
     * Natural balance of every account of [type] up to and including [target], per
     * currency — the accumulated-balance read expressed by nature of account rather
     * than with one nature fixed in its own text. The consolidated figure of two
     * natures is their **sum** (`MoneyByCurrency.plus`), since liabilities are stored
     * in credit; there is no second aggregate for it and no sign rule of its own.
     */
    suspend fun naturalBalanceUpToByCurrency(
        target: YearMonth,
        type: AccountType,
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

    /** The income/expense/adjustment/invoice-payment flows of [accountId] in [month]. */
    suspend fun accountFlows(month: YearMonth, accountId: Long): AccountFlows

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
     */
    suspend fun assetMonthFlowsByCurrency(month: YearMonth): AssetMonthFlowsByCurrency

    // No net-worth read here, deliberately (task 4.11). `Σ ASSET − Σ LIABILITY` is a
    // real capability and `ledger-reporting` requires it per currency — but the figure
    // the dashboard shows comes through `CalculateBalanceUseCase(accountId = null)`,
    // which is a different read (ASSET only, up to a target month). A member with no
    // production caller is a signature 21 fakes have to carry, so the read lives where
    // it does have one: `EntryDao.netWorthCents()`, already grouped by currency and
    // consumed by the migration-parity checks. Giving it a screen is its own change
    // (design D6 defers exposing the exchange result), and reinstating a one-line
    // member then is trivial.

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
