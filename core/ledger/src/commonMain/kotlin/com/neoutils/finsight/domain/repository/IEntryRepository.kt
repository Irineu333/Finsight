package com.neoutils.finsight.domain.repository

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.CurrencyBalance
import com.neoutils.finsight.domain.model.Entry
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth

/**
 * A figure scoped to a single account: one number, denominated in that account's own
 * [currency] — single-currency by construction, since currency is an attribute of the
 * account and the writer denominates every entry from the account it lands on.
 *
 * The currency comes back even though the caller usually holds the [Account] already, and
 * the two can never disagree. It travels with the figure so the figure describes itself,
 * for the same reason the display type carries it: a number whose denomination is resolved
 * somewhere else is the defect this change exists to remove.
 */
data class AccountBalance(val currency: String, val amount: Double)

/**
 * The per-account, per-period money flows an account screen shows, derived from the ledger
 * and denominated in the account's own [currency]. [adjustment] is signed; the rest are
 * positive magnitudes.
 */
data class AccountFlows(
    val currency: String,
    val income: Double,
    val expense: Double,
    val adjustment: Double,
    val settlement: Double,
)

/**
 * The money flows of one sub-ledger, derived from the entries carrying its dimension.
 * [adjustment] is signed; the rest are positive magnitudes.
 *
 * Each figure is a [CurrencyBalance] and not a number: nothing in the ledger binds a
 * dimension to a single account, so its entries may be denominated in several currencies.
 * A facade that knows its own dimension is single-currency — a card invoice always lands
 * on one card — reduces each figure itself; the ledger MUST NOT presume it.
 */
data class DimensionFlows(
    val expense: CurrencyBalance = CurrencyBalance.zero,
    val advancePayment: CurrencyBalance = CurrencyBalance.zero,
    val adjustment: CurrencyBalance = CurrencyBalance.zero,
)

/**
 * Month-wide card expense/payment across every card, both positive, plus the signed
 * [adjustment] — in symmetry with [AssetMonthFlows], so an invoice adjustment has a class
 * of its own instead of disappearing from the report. Per currency, since the read spans
 * every card.
 */
data class LiabilityMonthFlows(
    val expense: CurrencyBalance = CurrencyBalance.zero,
    val payment: CurrencyBalance = CurrencyBalance.zero,
    val adjustment: CurrencyBalance = CurrencyBalance.zero,
)

/**
 * The month-wide income/expense/adjustment across every ASSET account, derived from the
 * ledger, per currency. Transfers and card payments are excluded — neither is income or
 * expense. [income]/[expense] are positive magnitudes; [adjustment] is signed.
 */
data class AssetMonthFlows(
    val income: CurrencyBalance = CurrencyBalance.zero,
    val expense: CurrencyBalance = CurrencyBalance.zero,
    val adjustment: CurrencyBalance = CurrencyBalance.zero,
)

/**
 * The report figures for an account/card scope over a period, derived from the ledger,
 * per currency — a scope may hold accounts of several. [income]/[expense] are positive
 * magnitudes; [balance] is signed and includes adjustments; [openingBalance] is the signed
 * scope balance before the period.
 */
data class ScopeStats(
    val income: CurrencyBalance = CurrencyBalance.zero,
    val expense: CurrencyBalance = CurrencyBalance.zero,
    val balance: CurrencyBalance = CurrencyBalance.zero,
    val openingBalance: CurrencyBalance = CurrencyBalance.zero,
)

/**
 * Reads derived from the double-entry ledger. Every figure is a sum of entry amounts
 * (debit-positive), so account balance, category spending and invoice owed all share one
 * mechanism — no per-type sign rule anywhere. Values are returned in the major unit of
 * their own currency.
 *
 * A read scoped to a single account is one number plus that account's currency; every read
 * able to span accounts is expressed **per currency** and is never reduced to one number
 * here. Reducing it is conversion, it needs a rate, and a read that multiplied entries by a
 * rate would stop being `Σ entries` — so it belongs above the ledger, where it is a
 * presentation choice.
 */
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
     * Natural balance of [accountId] up to and including [target], denominated in that
     * account's currency — a read scoped to one account is single-currency by construction.
     * Naming no account means every ASSET account, which spans them, so the figure comes
     * back per currency: see [naturalBalanceUpTo].
     */
    suspend fun balanceUpTo(target: YearMonth, accountId: Long): AccountBalance

    /**
     * Natural balance of every account of [type] up to and including [target], per
     * currency — the accumulated-balance read expressed by nature of account rather than
     * with one nature fixed in its own text. The consolidated figure of two natures is
     * their **sum** ([CurrencyBalance.plus]), since liabilities are stored in credit;
     * there is no second aggregate for it and no sign rule of its own.
     */
    suspend fun naturalBalanceUpTo(target: YearMonth, type: AccountType): CurrencyBalance

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

    /** All-time natural balance of [accountId], across every date. */
    suspend fun balance(accountId: Long): AccountBalance

    /**
     * Natural balance of [dimensionId] within [month], per currency — used for category
     * spending, whose entries may be denominated in several.
     */
    suspend fun dimensionBalanceInMonth(month: YearMonth, dimensionId: Long): CurrencyBalance

    /** The income/expense/adjustment/invoice-payment flows of [accountId] in [month]. */
    suspend fun accountFlows(month: YearMonth, accountId: Long): AccountFlows

    /** Number of ledger entries carrying [dimensionId] within [month]. */
    suspend fun dimensionEntryCountInMonth(month: YearMonth, dimensionId: Long): Int

    /**
     * Amount owed on a sub-ledger (positive), from the entries carrying its dimension, per
     * currency. A facade whose dimension is single-currency — a card invoice — reduces the
     * figure itself; the ledger does not consult the dimension's kind to do it for it.
     */
    suspend fun dimensionOwed(dimensionId: Long): CurrencyBalance

    /** The expense/advance-payment/adjustment breakdown of a sub-ledger, from the ledger. */
    suspend fun dimensionFlows(dimensionId: Long): DimensionFlows

    /**
     * The owed total of each sub-ledger in [dimensionIds], keyed by dimension — the
     * batched [dimensionOwed] a screen listing many invoices needs. A dimension with
     * no entries is absent from the map (its owed is 0). The default fans out over
     * [dimensionOwed]; the ledger's own implementation overrides it with a single
     * grouped query so N invoices cost one read, not N.
     */
    suspend fun owedByDimension(dimensionIds: Collection<Long>): Map<Long, CurrencyBalance> =
        dimensionIds.distinct().associateWith { dimensionOwed(it) }

    /**
     * The flows breakdown of each sub-ledger in [dimensionIds], keyed by dimension —
     * the batched [dimensionFlows]. Same one-query-not-N contract as [owedByDimension].
     */
    suspend fun flowsByDimension(dimensionIds: Collection<Long>): Map<Long, DimensionFlows> =
        dimensionIds.distinct().associateWith { dimensionFlows(it) }

    /** Month-wide card expense/payment across every card account. */
    suspend fun liabilityMonthFlows(month: YearMonth): LiabilityMonthFlows

    /**
     * The month-wide income/expense/adjustment across every ASSET account, excluding
     * transfers and card payments — the summary a transaction list or dashboard shows.
     */
    suspend fun assetMonthFlows(month: YearMonth): AssetMonthFlows

    /**
     * Natural balance per dimension, per currency, of the [nominalType] legs in a date
     * range, counting only transactions that also have a leg on one of
     * [siblingAccountIds] — i.e. spending/income "seen from" those accounts.
     *
     * The `null` key is the unclassified total: legs on a nominal account carrying
     * no dimension. It is a group of the same aggregate, not a separate read.
     */
    suspend fun totalsByDimension(
        nominalType: AccountType,
        startDate: LocalDate,
        endDate: LocalDate,
        siblingAccountIds: List<Long>,
    ): Map<Long?, CurrencyBalance>

    /** The same totals, scoped to the transactions touching a set of sub-ledgers. */
    suspend fun totalsByDimensionInScope(
        nominalType: AccountType,
        scopeDimensionIds: List<Long>,
    ): Map<Long?, CurrencyBalance>

    /**
     * The income/expense/balance/opening-balance a report shows for an account or card
     * scope, over [startDate]..[endDate], derived from the ledger. [scopeAccountIds] are
     * the accounts the report is seen from (a perspective's ASSET accounts, or a card's
     * LIABILITY account); internal transfers among them are excluded. Empty scope yields
     * zeros — the caller resolves "all accounts" before calling.
     */
    suspend fun scopeStats(
        scopeAccountIds: List<Long>,
        startDate: LocalDate,
        endDate: LocalDate,
    ): ScopeStats
}

/**
 * Natural balance per currency of each dimension in [dimensionIds] within [month]. A thin
 * fan over [IEntryRepository.dimensionBalanceInMonth] so callers in different feature
 * `impl`s share one way to gather per-dimension month balances from the ledger.
 */
suspend fun IEntryRepository.dimensionBalancesInMonth(
    month: YearMonth,
    dimensionIds: Collection<Long>,
): Map<Long, CurrencyBalance> =
    dimensionIds.distinct().associateWith { dimensionBalanceInMonth(month, it) }
