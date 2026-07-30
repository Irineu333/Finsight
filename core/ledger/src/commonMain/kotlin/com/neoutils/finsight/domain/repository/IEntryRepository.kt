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

/**
 * The money flows of one sub-ledger, derived from the entries carrying its
 * dimension. [adjustment] is signed; the rest are positive magnitudes.
 */
@Deprecated(SCALAR_DEPRECATION, ReplaceWith("DimensionFlowsByCurrency"))
data class DimensionFlows(
    val expense: Double,
    val advancePayment: Double,
    val adjustment: Double,
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

/**
 * Month-wide card expense/payment across every card, both positive, plus the
 * signed [adjustment] — in symmetry with [AssetMonthFlows], so an invoice adjustment
 * has a class of its own instead of disappearing from the report.
 */
@Deprecated(SCALAR_DEPRECATION, ReplaceWith("LiabilityMonthFlowsByCurrency"))
data class LiabilityMonthFlows(
    val expense: Double,
    val payment: Double,
    val adjustment: Double,
)

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
 * The month-wide income/expense/adjustment across every ASSET account, derived from
 * the ledger. Transfers and card payments are excluded — neither is income or
 * expense. [income]/[expense] are positive magnitudes; [adjustment] is signed.
 */
@Deprecated(SCALAR_DEPRECATION, ReplaceWith("AssetMonthFlowsByCurrency"))
data class AssetMonthFlows(
    val income: Double,
    val expense: Double,
    val adjustment: Double,
)

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

/**
 * The report figures for an account/card scope over a period, derived from the
 * ledger. [income]/[expense] are positive magnitudes; [balance] is signed and includes
 * adjustments; [openingBalance] is the signed scope balance before the period.
 */
@Deprecated(SCALAR_DEPRECATION, ReplaceWith("ScopeStatsByCurrency"))
data class ScopeStats(
    val income: Double,
    val expense: Double,
    val balance: Double,
    val openingBalance: Double,
)

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

internal const val SCALAR_DEPRECATION: String =
    "Sums currencies into one number. Use the per-currency read (design D8); this " +
        "goes away once every caller has migrated (task 13.1)."

/**
 * The body every per-currency read below carries while the app migrates to it.
 *
 * It is what lets the per-currency surface land without touching the 21 hand-written
 * fakes of this interface: a member with a body is invisible to whoever does not
 * implement it, and an abstract one would break every fake at once. Task 13.1 removes
 * **the bodies**, not the signatures — that removal is the mechanical proof that
 * nobody was left behind. A *working* body is impossible here: it would need a
 * currency, and design D28 takes the ledger's opinion on which one away.
 */
private fun noPerCurrencyImplementation(): Nothing = throw NotImplementedError(
    "This per-currency ledger read has no implementation in this class. Only the " +
        "ledger's own EntryRepository implements it; a fake that reaches this has " +
        "not been migrated yet.",
)

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

    @Deprecated(SCALAR_DEPRECATION)
    suspend fun balanceUpTo(target: YearMonth, accountId: Long? = null): Double

    /**
     * Natural balance of [accountId] up to and including [target]. Scalar, and it
     * stays scalar: one account is one currency, which the caller already knows.
     *
     * A name of its own rather than an overload of [balanceUpTo]: the two would have
     * the same parameter *names*, so every existing named-argument call would silently
     * re-resolve to whichever signature is more specific — a caller moved to a
     * different member without anybody editing it.
     */
    suspend fun accountBalanceUpTo(accountId: Long, target: YearMonth): Double =
        noPerCurrencyImplementation()

    /**
     * Natural balance of every ASSET account up to and including [target], per
     * currency — the read the dashboard's total balance comes through, and therefore
     * the door multi-currency enters the app by.
     */
    suspend fun balanceUpToByCurrency(target: YearMonth): MoneyByCurrency =
        noPerCurrencyImplementation()

    @Deprecated(SCALAR_DEPRECATION)
    suspend fun naturalBalanceUpTo(target: YearMonth, type: AccountType): Double

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
    ): MoneyByCurrency = noPerCurrencyImplementation()

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

    @Deprecated(SCALAR_DEPRECATION)
    suspend fun dimensionBalanceInMonth(month: YearMonth, dimensionId: Long): Double

    /** Natural balance of [dimensionId] within [month], per currency — category spending. */
    suspend fun dimensionBalanceInMonthByCurrency(
        month: YearMonth,
        dimensionId: Long,
    ): MoneyByCurrency = noPerCurrencyImplementation()

    /** The income/expense/adjustment/invoice-payment flows of [accountId] in [month]. */
    suspend fun accountFlows(month: YearMonth, accountId: Long): AccountFlows

    /** Number of ledger entries carrying [dimensionId] within [month]. */
    suspend fun dimensionEntryCountInMonth(month: YearMonth, dimensionId: Long): Int

    @Deprecated(SCALAR_DEPRECATION)
    suspend fun dimensionOwed(dimensionId: Long): Double

    /** Amount owed on a sub-ledger (positive), per currency, from the entries carrying its dimension. */
    suspend fun dimensionOwedByCurrency(dimensionId: Long): MoneyByCurrency =
        noPerCurrencyImplementation()

    @Deprecated(SCALAR_DEPRECATION)
    suspend fun dimensionFlows(dimensionId: Long): DimensionFlows

    /** The expense/advance-payment/adjustment breakdown of a sub-ledger, per currency. */
    suspend fun dimensionFlowsByCurrency(dimensionId: Long): DimensionFlowsByCurrency =
        noPerCurrencyImplementation()

    /**
     * The owed total of each sub-ledger in [dimensionIds], keyed by dimension — the
     * batched [dimensionOwed] a screen listing many invoices needs. A dimension with
     * no entries is absent from the map (its owed is 0). The default fans out over
     * [dimensionOwed]; the ledger's own implementation overrides it with a single
     * grouped query so N invoices cost one read, not N.
     */
    @Deprecated(SCALAR_DEPRECATION)
    suspend fun owedByDimension(dimensionIds: Collection<Long>): Map<Long, Double> =
        dimensionIds.distinct().associateWith { dimensionOwed(it) }

    /**
     * The owed total of each sub-ledger in [dimensionIds], per currency, keyed by
     * dimension. Same one-query-not-N contract as [owedByDimension].
     */
    suspend fun owedByDimensionByCurrency(
        dimensionIds: Collection<Long>,
    ): Map<Long, MoneyByCurrency> = noPerCurrencyImplementation()

    /**
     * The flows breakdown of each sub-ledger in [dimensionIds], keyed by dimension —
     * the batched [dimensionFlows]. Same one-query-not-N contract as [owedByDimension].
     */
    @Deprecated(SCALAR_DEPRECATION)
    suspend fun flowsByDimension(dimensionIds: Collection<Long>): Map<Long, DimensionFlows> =
        dimensionIds.distinct().associateWith { dimensionFlows(it) }

    /** The flows breakdown of each sub-ledger in [dimensionIds], per currency. */
    suspend fun flowsByDimensionByCurrency(
        dimensionIds: Collection<Long>,
    ): Map<Long, DimensionFlowsByCurrency> = noPerCurrencyImplementation()

    @Deprecated(SCALAR_DEPRECATION)
    suspend fun liabilityMonthFlows(month: YearMonth): LiabilityMonthFlows

    /** Month-wide card expense/payment across every card account, per currency. */
    suspend fun liabilityMonthFlowsByCurrency(month: YearMonth): LiabilityMonthFlowsByCurrency =
        noPerCurrencyImplementation()

    @Deprecated(SCALAR_DEPRECATION)
    suspend fun assetMonthFlows(month: YearMonth): AssetMonthFlows

    /**
     * The month-wide income/expense/adjustment across every ASSET account, per
     * currency, excluding transfers and card payments — the summary a transaction
     * list or dashboard shows.
     */
    suspend fun assetMonthFlowsByCurrency(month: YearMonth): AssetMonthFlowsByCurrency =
        noPerCurrencyImplementation()

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
    @Deprecated(SCALAR_DEPRECATION)
    suspend fun totalsByDimension(
        nominalType: AccountType,
        startDate: LocalDate,
        endDate: LocalDate,
        siblingAccountIds: List<Long>,
    ): Map<Long?, Double>

    /** The same totals, per currency. */
    suspend fun totalsByDimensionByCurrency(
        nominalType: AccountType,
        startDate: LocalDate,
        endDate: LocalDate,
        siblingAccountIds: List<Long>,
    ): Map<Long?, MoneyByCurrency> = noPerCurrencyImplementation()

    /** The same totals, scoped to the transactions touching a set of sub-ledgers. */
    @Deprecated(SCALAR_DEPRECATION)
    suspend fun totalsByDimensionInScope(
        nominalType: AccountType,
        scopeDimensionIds: List<Long>,
    ): Map<Long?, Double>

    /** The same scoped totals, per currency. */
    suspend fun totalsByDimensionInScopeByCurrency(
        nominalType: AccountType,
        scopeDimensionIds: List<Long>,
    ): Map<Long?, MoneyByCurrency> = noPerCurrencyImplementation()

    /**
     * The income/expense/balance/opening-balance a report shows for an account or card
     * scope, over [startDate]..[endDate], derived from the ledger. [scopeAccountIds] are
     * the accounts the report is seen from (a perspective's ASSET accounts, or a card's
     * LIABILITY account); internal transfers among them are excluded. Empty scope yields
     * zeros — the caller resolves "all accounts" before calling.
     */
    @Deprecated(SCALAR_DEPRECATION)
    suspend fun scopeStats(
        scopeAccountIds: List<Long>,
        startDate: LocalDate,
        endDate: LocalDate,
    ): ScopeStats

    /**
     * The same report figures, per currency. The empty scope yields
     * [ScopeStatsByCurrency.zero] — figures about nothing, in no currency.
     */
    suspend fun scopeStatsByCurrency(
        scopeAccountIds: List<Long>,
        startDate: LocalDate,
        endDate: LocalDate,
    ): ScopeStatsByCurrency = noPerCurrencyImplementation()
}

/**
 * Natural balance of each dimension in [dimensionIds] within [month]. A thin
 * fan over [IEntryRepository.dimensionBalanceInMonth] so callers in different feature
 * `impl`s share one way to gather per-dimension month balances from the ledger.
 */
@Deprecated(SCALAR_DEPRECATION)
suspend fun IEntryRepository.dimensionBalancesInMonth(
    month: YearMonth,
    dimensionIds: Collection<Long>,
): Map<Long, Double> = dimensionIds.distinct().associateWith { dimensionBalanceInMonth(month, it) }

/** The same fan, per currency — one figure per dimension, each expressed by currency. */
suspend fun IEntryRepository.dimensionBalancesInMonthByCurrency(
    month: YearMonth,
    dimensionIds: Collection<Long>,
): Map<Long, MoneyByCurrency> =
    dimensionIds.distinct().associateWith { dimensionBalanceInMonthByCurrency(month, it) }
