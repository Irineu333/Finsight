package com.neoutils.finsight.domain.repository

import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Entry
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth

/**
 * Reads derived from the double-entry ledger. Every figure is a sum of entry
 * amounts (debit-positive), so account balance, category spending, invoice owed
 * and net worth all share one mechanism — no per-type sign rule anywhere.
 * Values are returned in the major currency unit (reais).
 */
/**
 * The per-account, per-period money flows (reais) an account screen shows, derived
 * from the ledger. [adjustment] is signed; the rest are positive magnitudes.
 */
data class AccountFlows(
    val income: Double,
    val expense: Double,
    val adjustment: Double,
    val invoicePayment: Double,
)

/**
 * The per-invoice money flows (reais) a card invoice screen shows, derived from the
 * ledger. [adjustment] is signed; the rest are positive magnitudes.
 */
data class InvoiceFlows(
    val expense: Double,
    val advancePayment: Double,
    val adjustment: Double,
)

/** Month-wide card expense/payment (reais) across every card, both positive. */
data class CardMonthFlows(
    val expense: Double,
    val payment: Double,
)

/**
 * The report figures (reais) for an account/card scope over a period, derived from the
 * ledger. [income]/[expense] are positive magnitudes; [balance] is signed and includes
 * adjustments; [openingBalance] is the signed scope balance before the period.
 */
data class ReportStats(
    val income: Double,
    val expense: Double,
    val balance: Double,
    val openingBalance: Double,
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

    /**
     * Natural balance of [accountId] (or of all ASSET accounts when null) up to
     * and including [target].
     */
    suspend fun balanceUpTo(target: YearMonth, accountId: Long? = null): Double

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
    suspend fun balance(accountId: Long): Double

    /** Natural balance of [dimensionId] within [month] — used for category spending. */
    suspend fun dimensionBalanceInMonth(month: YearMonth, dimensionId: Long): Double

    /** The income/expense/adjustment/invoice-payment flows of [accountId] in [month]. */
    suspend fun accountFlows(month: YearMonth, accountId: Long): AccountFlows

    /** Number of ledger entries carrying [dimensionId] within [month]. */
    suspend fun dimensionEntryCountInMonth(month: YearMonth, dimensionId: Long): Int

    /** Amount owed on a sub-ledger (positive), from the entries carrying its dimension. */
    suspend fun dimensionOwed(dimensionId: Long): Double

    /** The expense/advance-payment/adjustment breakdown of a sub-ledger, from the ledger. */
    suspend fun dimensionFlows(dimensionId: Long): InvoiceFlows

    /** Month-wide card expense/payment across every card account. */
    suspend fun cardMonthFlows(month: YearMonth): CardMonthFlows

    /** Net worth = Σ ASSET − Σ LIABILITY, via the same entry mechanism. */
    suspend fun netWorth(): Double

    /**
     * Natural balance (reais) per dimension of the [nominalType] legs in a date
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
    ): Map<Long?, Double>

    /** The same totals, scoped to the transactions touching a set of sub-ledgers. */
    suspend fun totalsByDimensionInScope(
        nominalType: AccountType,
        scopeDimensionIds: List<Long>,
    ): Map<Long?, Double>

    /**
     * The income/expense/balance/opening-balance a report shows for an account or card
     * scope, over [startDate]..[endDate], derived from the ledger. [scopeAccountIds] are
     * the accounts the report is seen from (a perspective's ASSET accounts, or a card's
     * LIABILITY account); internal transfers among them are excluded. Empty scope yields
     * zeros — the caller resolves "all accounts" before calling.
     */
    suspend fun reportStats(
        scopeAccountIds: List<Long>,
        startDate: LocalDate,
        endDate: LocalDate,
    ): ReportStats
}

/**
 * Natural balance (reais) of each dimension in [dimensionIds] within [month]. A thin
 * fan over [IEntryRepository.dimensionBalanceInMonth] so callers in different feature
 * `impl`s share one way to gather per-dimension month balances from the ledger.
 */
suspend fun IEntryRepository.dimensionBalancesInMonth(
    month: YearMonth,
    dimensionIds: Collection<Long>,
): Map<Long, Double> = dimensionIds.distinct().associateWith { dimensionBalanceInMonth(month, it) }
