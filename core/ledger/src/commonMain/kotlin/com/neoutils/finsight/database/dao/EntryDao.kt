package com.neoutils.finsight.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import com.neoutils.finsight.database.entity.AccountEntity
import com.neoutils.finsight.database.entity.EntryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

/**
 * What every row of a `GROUP BY currency` aggregate has in common: the currency its
 * figures are denominated in. It exists so the repository lifts any of those
 * projections into a per-currency figure through one path instead of one per
 * projection.
 */
interface CurrencyScoped {
    val currency: String
}

/**
 * One currency's share of a grouped aggregate (cents). Every aggregate that is not
 * scoped to a single account returns a list of these, one row per currency present —
 * the ledger never sums two currencies into one number (design D8).
 *
 * **A grouped aggregate has no empty row.** `COALESCE(SUM(...), 0)` without a
 * `GROUP BY` always returns one row of zeros; with one it returns *no rows* when
 * nothing matched. An empty list is therefore the honest answer to "there is no
 * movement", and it is a different fact from a row whose total is zero.
 */
data class CurrencyTotal(override val currency: String, val total: Long) : CurrencyScoped

/**
 * One currency's share of a per-dimension grouped aggregate (cents).
 *
 * A `null` [dimensionId] is the legitimate *unclassified* bucket — entries on a
 * nominal account carrying no dimension — and not an absence of data: it is the same
 * `GROUP BY`, one of whose groups happens to be "none".
 */
data class DimensionCurrencyTotal(
    val dimensionId: Long?,
    override val currency: String,
    val total: Long,
) : CurrencyScoped

/**
 * One month's share of a per-month grouped aggregate (cents), in one currency.
 *
 * [yearMonth] is the `yyyy-MM` the entries fell in — the same cut every monthly read
 * of this DAO makes, only projected instead of filtered. A month with no entry has no
 * row, exactly as a currency with no entry has none: a grouped aggregate has no empty
 * row, and whoever needs zeros in a window supplies them where the window is decided.
 */
data class MonthCurrencyTotal(
    val yearMonth: String,
    override val currency: String,
    val total: Long,
) : CurrencyScoped

/**
 * The per-account, per-period money flows (cents) an account screen shows, derived
 * from the ledger and classified by the transaction's counter-legs — the ledger
 * equivalent of the legacy `AccountUi` sums:
 *  - [income]/[expense]: transactions with neither an EQUITY nor a LIABILITY leg,
 *    split by the sign of the account's own entry (this includes a transfer's two
 *    legs, exactly as the legacy leg types EXPENSE/INCOME did);
 *  - [yield]: the part of [income] whose income counter-leg carries the caller's
 *    dimension. It **repartitions** [income] rather than adding to it — what it
 *    shows, [income] no longer does — so the lines still sum to Σ entries;
 *  - [adjustment]: transactions with an EQUITY counter-leg, kept signed;
 *  - [settlement]: transactions with a LIABILITY counter-leg — paying off a debt.
 * All are positive magnitudes except [adjustment], which is signed.
 */
data class AccountPeriodTotals(
    // The account's own currency, not a group: this read is scoped to one account,
    // so it stays scalar and simply says what its figures are denominated in.
    val currency: String,
    val income: Long,
    val yield: Long,
    val expense: Long,
    val adjustment: Long,
    val settlement: Long,
)

/**
 * The money flows (cents) of one sub-ledger **in one currency**, from the entries
 * carrying its dimension. [expense]/[advancePayment] are positive magnitudes;
 * [adjustment] is signed.
 */
data class DimensionPeriodTotals(
    override val currency: String,
    val expense: Long,
    val advancePayment: Long,
    val adjustment: Long,
) : CurrencyScoped

/**
 * [DimensionPeriodTotals] keyed by its dimension, for the batched grouped read —
 * one row per (dimension, currency) pair.
 */
data class DimensionPeriodTotalsRow(
    val dimensionId: Long,
    override val currency: String,
    val expense: Long,
    val advancePayment: Long,
    val adjustment: Long,
) : CurrencyScoped

/**
 * Month-wide card [expense]/[payment] (cents) **in one currency**, both positive
 * magnitudes, plus the signed [adjustment] — the EQUITY counter-leg — in symmetry
 * with [AssetMonthTotals].
 */
data class LiabilityMonthTotals(
    override val currency: String,
    val expense: Long,
    val payment: Long,
    val adjustment: Long,
) : CurrencyScoped

/**
 * The month-wide income/expense/adjustment (cents) across every ASSET account,
 * classified by each transaction's counter-legs — the "money in / money out" a
 * transaction list or dashboard summarises. Transfers and card payments move money
 * between the user's own accounts and are neither, so they are excluded. [income]/
 * [expense] are positive magnitudes; [adjustment] is signed. [yield] repartitions
 * [income] — it is the slice whose income counter-leg carries the caller's dimension,
 * and [income] no longer contains it. See [EntryDao.assetMonthTotals].
 */
data class AssetMonthTotals(
    override val currency: String,
    val income: Long,
    val yield: Long,
    val expense: Long,
    val adjustment: Long,
) : CurrencyScoped

/**
 * The report figures for an account/card scope over a period, all in cents. [income]/
 * [expense] are positive magnitudes of the scope legs classified by counter-leg;
 * [balance] is their signed sum within the period (adjustments included); [openingBalance]
 * is the signed sum of the scope legs before the period. Internal transfers — a
 * transaction whose ASSET legs all fall inside the scope — are excluded on both sides.
 */
data class ScopeStatsTotals(
    override val currency: String,
    val income: Long,
    val expense: Long,
    val balance: Long,
    val openingBalance: Long,
) : CurrencyScoped

/** An [EntryEntity] with its referenced [AccountEntity] resolved — a complete leg. */
data class EntryWithAccount(
    @Embedded val entry: EntryEntity,
    @Relation(parentColumn = "accountId", entityColumn = "id")
    val account: AccountEntity,
)

@Dao
interface EntryDao {

    @Insert
    suspend fun insert(entry: EntryEntity): Long

    @Insert
    suspend fun insertAll(entries: List<EntryEntity>): List<Long>


    @Query("DELETE FROM entries WHERE transactionId = :transactionId")
    suspend fun deleteByTransactionId(transactionId: Long)

    @Query("SELECT * FROM entries ORDER BY id ASC")
    suspend fun getAll(): List<EntryEntity>

    @Query("SELECT * FROM entries ORDER BY id ASC")
    fun observeAll(): Flow<List<EntryEntity>>

    /**
     * A cheap invalidation signal for readers that derive their figures from SQL
     * aggregates instead of from the entry rows. Room re-runs a `Flow` query on
     * every write to the table, so the value itself is irrelevant — what matters is
     * that it emits whenever the ledger changed.
     */
    @Query("SELECT COUNT(*) FROM entries")
    fun observeEntryCount(): Flow<Long>

    /** Whether an account has any movement at all — cheaper than counting it. */
    @Query("SELECT EXISTS(SELECT 1 FROM entries WHERE accountId = :accountId)")
    suspend fun hasEntries(accountId: Long): Boolean

    /**
     * The same fact for a facade that owns a dimension instead of an account. It is
     * what decides delete-vs-archive for a category, exactly as [hasEntries] does
     * for an account or a card — one mechanism, two keys.
     */
    @Query("SELECT EXISTS(SELECT 1 FROM entries WHERE dimensionId = :dimensionId)")
    suspend fun hasEntriesForDimension(dimensionId: Long): Boolean

    @Query("SELECT * FROM entries WHERE transactionId = :transactionId ORDER BY id ASC")
    suspend fun getByTransactionId(transactionId: Long): List<EntryEntity>

    /**
     * The legs of several transactions in one read — what keeps a batch of transactions
     * from costing one entry query per row — grouped by the transaction they balance
     * under and ordered within it as [getByTransactionId] orders them.
     *
     * One host parameter is bound per identity, so the list a caller passes is bounded
     * by SQLite's own ceiling on them. Chunking to stay under it belongs to the caller
     * (`TransactionRepository`), which is why this stays the plain query it looks like.
     */
    @Query("SELECT * FROM entries WHERE transactionId IN (:transactionIds) ORDER BY transactionId ASC, id ASC")
    suspend fun getByTransactionIds(transactionIds: Collection<Long>): List<EntryEntity>

    /** Entries of a transaction, each hydrated with its account — a complete leg. */
    @Transaction
    @Query("SELECT * FROM entries WHERE transactionId = :transactionId ORDER BY id ASC")
    suspend fun getEntriesWithAccountByTransactionId(transactionId: Long): List<EntryWithAccount>

    /** Observes the entries of a transaction, each hydrated with its account. */
    @Transaction
    @Query("SELECT * FROM entries WHERE transactionId = :transactionId ORDER BY id ASC")
    fun observeEntriesWithAccountByTransactionId(transactionId: Long): Flow<List<EntryWithAccount>>



    /** All-time natural balance of an account, across every date and currency. */
    @Query("SELECT COALESCE(SUM(amount), 0) FROM entries WHERE accountId = :accountId")
    suspend fun balanceOf(accountId: Long): Long

    // --- Ledger reads (natural, debit-positive cents). All derive from Σ amount. ---

    /**
     * Natural balance of an account up to and including the given date (yyyy-MM-dd).
     *
     * The cut is by day because the transaction date already has that resolution. The
     * accumulated balance up to a month is this same read asked at the month's last day,
     * not a second query with a coarser cut.
     *
     * The per-currency reads below keep their monthly cut: no consumer of theirs asks by
     * day, and the asymmetry is deliberate.
     */
    @Query(
        "SELECT COALESCE(SUM(e.amount), 0) FROM entries e " +
            "JOIN transactions o ON o.id = e.transactionId " +
            "WHERE e.accountId = :accountId AND o.date <= :date"
    )
    suspend fun balanceUpToDate(accountId: Long, date: String): Long

    /**
     * Combined natural balance of every account of one nature up to and including the
     * month, **per currency**. The nature is a parameter, not a literal, so the same
     * aggregate serves ASSET and LIABILITY — and their consolidated figure is the sum
     * of two calls, since liabilities are stored in credit.
     *
     * [excludedAccountIds] narrows the nature to a subset of its accounts, inside this
     * same query — there is no second path to the accumulated balance and no subtraction
     * of individual balances from a total. An empty set excludes nothing (`NOT IN ()` is
     * true for every row in SQLite), so it is the read as it always was. The filter reads
     * `e.accountId` rather than `a.id`: the same value through the join, without asking
     * the joined table for what the entry already carries.
     */
    @Query(
        "SELECT e.currency AS currency, COALESCE(SUM(e.amount), 0) AS total FROM entries e " +
            "JOIN transactions o ON o.id = e.transactionId " +
            "JOIN accounts a ON a.id = e.accountId " +
            "WHERE a.type = :type AND substr(o.date, 1, 7) <= :yearMonth " +
            "AND e.accountId NOT IN (:excludedAccountIds) " +
            "GROUP BY e.currency"
    )
    suspend fun balanceUpToMonthByType(
        type: String,
        yearMonth: String,
        excludedAccountIds: Collection<Long>,
    ): List<CurrencyTotal>

    /**
     * Natural balance of a dimension within a single month (yyyy-MM), per currency.
     * A dimension is an analytic axis, not an account: nothing ties it to a single
     * currency, so this read is grouped whatever the dimension's kind is (design D8).
     */
    @Query(
        "SELECT e.currency AS currency, COALESCE(SUM(e.amount), 0) AS total FROM entries e " +
            "JOIN transactions o ON o.id = e.transactionId " +
            "WHERE e.dimensionId = :dimensionId AND substr(o.date, 1, 7) = :yearMonth " +
            "GROUP BY e.currency"
    )
    suspend fun dimensionBalanceInMonth(
        dimensionId: Long,
        yearMonth: String,
    ): List<CurrencyTotal>

    /**
     * The same aggregate as [dimensionBalanceInMonth], grouped **by month** instead of
     * filtered to one: a dimension's whole series in a single read, per month and per
     * currency. A window of N months costs one query, not N.
     *
     * [untilYearMonth] is the upper cut, inclusive, and it is a parameter because entries
     * dated in the future are an ordinary state of the ledger — a purchase in instalments
     * writes them. The ledger decides no period and knows no "current month": what to read
     * up to is the caller's to say, exactly as it is for the scalar accumulated balance.
     *
     * `ORDER BY` is part of the read, not decoration: the series is consumed in month
     * order, and SQL row order is otherwise no promise.
     */
    @Query(
        "SELECT substr(o.date, 1, 7) AS yearMonth, e.currency AS currency, " +
            "COALESCE(SUM(e.amount), 0) AS total FROM entries e " +
            "JOIN transactions o ON o.id = e.transactionId " +
            "WHERE e.dimensionId = :dimensionId AND substr(o.date, 1, 7) <= :untilYearMonth " +
            "GROUP BY substr(o.date, 1, 7), e.currency " +
            "ORDER BY substr(o.date, 1, 7)"
    )
    suspend fun dimensionMonthlySeries(
        dimensionId: Long,
        untilYearMonth: String,
    ): List<MonthCurrencyTotal>

    /**
     * Per-dimension totals of the nominal legs of a given nature within a single month
     * (yyyy-MM), per currency — a whole breakdown in one read instead of one read per
     * dimension.
     *
     * The unclassified legs come back as the `null` group, by the same mechanism, not
     * by a second query and not through a bucket account.
     *
     * The `a.type = :nominalType` filter is not an optimisation: without it
     * `dimensionId IS NULL` would match every unclassified leg in the ledger — asset,
     * liability, conversion — and the null group would stop being a total about
     * classification.
     */
    @Query(
        """
        SELECT e.dimensionId AS dimensionId, e.currency AS currency,
          COALESCE(SUM(e.amount), 0) AS total
        FROM entries e
        JOIN transactions o ON o.id = e.transactionId
        JOIN accounts a ON a.id = e.accountId
        WHERE a.type = :nominalType AND substr(o.date, 1, 7) = :yearMonth
        GROUP BY e.dimensionId, e.currency
        """
    )
    suspend fun totalsByDimensionInMonth(
        nominalType: String,
        yearMonth: String,
    ): List<DimensionCurrencyTotal>

    /**
     * Natural balance of a sub-ledger, per currency = Σ the entries tagged with its
     * dimension, grouped.
     */
    @Query(
        "SELECT currency AS currency, COALESCE(SUM(amount), 0) AS total " +
            "FROM entries WHERE dimensionId = :dimensionId GROUP BY currency"
    )
    suspend fun dimensionNaturalBalance(dimensionId: Long): List<CurrencyTotal>

    /**
     * The natural balance of each dimension in [dimensionIds], per currency, in one
     * grouped query, so a screen listing many sub-ledgers reads them all at once
     * instead of one query per dimension. A dimension with no entries is simply
     * absent from the result (its balance is 0).
     */
    @Query(
        "SELECT dimensionId AS dimensionId, currency AS currency, " +
            "COALESCE(SUM(amount), 0) AS total " +
            "FROM entries WHERE dimensionId IN (:dimensionIds) " +
            "GROUP BY dimensionId, currency"
    )
    suspend fun naturalBalanceByDimension(
        dimensionIds: List<Long>,
    ): List<DimensionCurrencyTotal>

    /**
     * The account's income/yield/expense/adjustment/invoice-payment flows within a
     * month (yyyy-MM), classified by each transaction's counter-legs.
     *
     * [yieldDimensionId] is a plain dimension identity — the ledger does not know
     * what it names, and must not. It is a third counter-leg flag of the same shape
     * as `eq` and `li`, and `income` excludes exactly what `yield` includes, so the
     * lines keep partitioning Σ entries. Passing `null` never matches, `yl` is always
     * 0, and the totals are the ones this query produced before the flag existed —
     * the separation degrades to nothing with no conditional of its own.
     * See [AccountPeriodTotals].
     */
    @Query(
        """
        SELECT
          (SELECT a.currency FROM accounts a WHERE a.id = :accountId) AS currency,
          COALESCE(SUM(CASE WHEN eq = 0 AND li = 0 AND yl = 0 AND amount > 0 THEN amount ELSE 0 END), 0) AS income,
          COALESCE(SUM(CASE WHEN eq = 0 AND li = 0 AND yl = 1 AND amount > 0 THEN amount ELSE 0 END), 0) AS yield,
          COALESCE(SUM(CASE WHEN eq = 0 AND li = 0 AND amount < 0 THEN -amount ELSE 0 END), 0) AS expense,
          COALESCE(SUM(CASE WHEN eq = 1 THEN amount ELSE 0 END), 0) AS adjustment,
          COALESCE(SUM(CASE WHEN eq = 0 AND li = 1 THEN -amount ELSE 0 END), 0) AS settlement
        FROM (
          SELECT e.amount AS amount,
            EXISTS(SELECT 1 FROM entries x JOIN accounts a ON a.id = x.accountId WHERE x.transactionId = e.transactionId AND a.type = 'EQUITY') AS eq,
            EXISTS(SELECT 1 FROM entries x JOIN accounts a ON a.id = x.accountId WHERE x.transactionId = e.transactionId AND a.type = 'LIABILITY') AS li,
            EXISTS(SELECT 1 FROM entries x JOIN accounts a ON a.id = x.accountId WHERE x.transactionId = e.transactionId AND a.type = 'INCOME' AND x.dimensionId = :yieldDimensionId) AS yl
          FROM entries e
          JOIN transactions o ON o.id = e.transactionId
          WHERE e.accountId = :accountId AND substr(o.date, 1, 7) = :yearMonth
        )
        """
    )
    suspend fun accountPeriodTotals(
        accountId: Long,
        yearMonth: String,
        yieldDimensionId: Long?,
    ): AccountPeriodTotals

    /**
     * The expense/advance-payment/adjustment breakdown of a sub-ledger, from the
     * entries tagged with its dimension, classified by sign and by whether the
     * transaction also has an EQUITY counter-leg, grouped by currency. See
     * [DimensionPeriodTotals]. All are positive magnitudes except [adjustment], which
     * is signed.
     *
     * The currency has to be projected by the derived table before the outer
     * `GROUP BY` can group by it — the inner query is what reaches the entry rows.
     */
    @Query(
        """
        SELECT currency AS currency,
          COALESCE(SUM(CASE WHEN eq = 0 AND amount < 0 THEN -amount ELSE 0 END), 0) AS expense,
          COALESCE(SUM(CASE WHEN eq = 0 AND amount > 0 THEN amount ELSE 0 END), 0) AS advancePayment,
          COALESCE(SUM(CASE WHEN eq = 1 THEN amount ELSE 0 END), 0) AS adjustment
        FROM (
          SELECT e.amount AS amount, e.currency AS currency,
            EXISTS(SELECT 1 FROM entries x JOIN accounts a ON a.id = x.accountId WHERE x.transactionId = e.transactionId AND a.type = 'EQUITY') AS eq
          FROM entries e
          WHERE e.dimensionId = :dimensionId
        )
        GROUP BY currency
        """
    )
    suspend fun dimensionPeriodTotals(dimensionId: Long): List<DimensionPeriodTotals>

    /**
     * The same expense/advance-payment/adjustment breakdown as [dimensionPeriodTotals],
     * but for every dimension in [dimensionIds] at once — a single grouped read for a
     * screen showing many sub-ledgers. A dimension with no entries is absent from the
     * result (all its flows are 0).
     */
    @Query(
        """
        SELECT dimensionId AS dimensionId, currency AS currency,
          COALESCE(SUM(CASE WHEN eq = 0 AND amount < 0 THEN -amount ELSE 0 END), 0) AS expense,
          COALESCE(SUM(CASE WHEN eq = 0 AND amount > 0 THEN amount ELSE 0 END), 0) AS advancePayment,
          COALESCE(SUM(CASE WHEN eq = 1 THEN amount ELSE 0 END), 0) AS adjustment
        FROM (
          SELECT e.dimensionId AS dimensionId, e.amount AS amount, e.currency AS currency,
            EXISTS(SELECT 1 FROM entries x JOIN accounts a ON a.id = x.accountId WHERE x.transactionId = e.transactionId AND a.type = 'EQUITY') AS eq
          FROM entries e
          WHERE e.dimensionId IN (:dimensionIds)
        )
        GROUP BY dimensionId, currency
        """
    )
    suspend fun periodTotalsByDimension(dimensionIds: List<Long>): List<DimensionPeriodTotalsRow>

    /**
     * Month-wide card expense/advance-payment/adjustment across every LIABILITY (card)
     * account (yyyy-MM), classified by sign and EQUITY presence. Expense and payment are
     * positive magnitudes; adjustment is signed, exactly as in [assetMonthTotals] — an
     * invoice adjustment is neither a purchase nor a payment, and without a class of its
     * own it would simply vanish from the report.
     */
    @Query(
        """
        SELECT currency AS currency,
          COALESCE(SUM(CASE WHEN eq = 0 AND amount < 0 THEN -amount ELSE 0 END), 0) AS expense,
          COALESCE(SUM(CASE WHEN eq = 0 AND amount > 0 THEN amount ELSE 0 END), 0) AS payment,
          COALESCE(SUM(CASE WHEN eq = 1 THEN amount ELSE 0 END), 0) AS adjustment
        FROM (
          SELECT e.amount AS amount, e.currency AS currency,
            EXISTS(SELECT 1 FROM entries x JOIN accounts a2 ON a2.id = x.accountId WHERE x.transactionId = e.transactionId AND a2.type = 'EQUITY') AS eq
          FROM entries e
          JOIN accounts a ON a.id = e.accountId
          JOIN transactions o ON o.id = e.transactionId
          WHERE a.type = 'LIABILITY' AND substr(o.date, 1, 7) = :yearMonth
        )
        GROUP BY currency
        """
    )
    suspend fun liabilityMonthTotals(yearMonth: String): List<LiabilityMonthTotals>

    /**
     * The month-wide income/expense/adjustment across every ASSET account (yyyy-MM),
     * classified by each transaction's counter-legs. A transaction counts only when it
     * has a nominal (EXPENSE/INCOME) or EQUITY counter-leg — which is exactly "not a
     * transfer and not a card payment", the two forms that move money between the
     * user's own accounts. An EQUITY counter-leg is an adjustment (kept signed); the
     * rest split by the sign of the ASSET leg (money out = expense, money in = income),
     * so a refund on an expense reads as income by the same rule.
     *
     * [yieldDimensionId] repartitions income exactly as in [accountPeriodTotals], and
     * degrades to nothing when null. It is what lets the aggregate perimeter — every
     * ASSET account at once — separate yield without a per-account lookup: the
     * dimension is one and global, so both reads take the same `Long`.
     * See [AssetMonthTotals].
     */
    @Query(
        """
        SELECT currency AS currency,
          COALESCE(SUM(CASE WHEN eq = 0 AND yl = 0 AND amount > 0 THEN amount ELSE 0 END), 0) AS income,
          COALESCE(SUM(CASE WHEN eq = 0 AND yl = 1 AND amount > 0 THEN amount ELSE 0 END), 0) AS yield,
          COALESCE(SUM(CASE WHEN eq = 0 AND amount < 0 THEN -amount ELSE 0 END), 0) AS expense,
          COALESCE(SUM(CASE WHEN eq = 1 THEN amount ELSE 0 END), 0) AS adjustment
        FROM (
          SELECT e.amount AS amount, e.currency AS currency,
            EXISTS(SELECT 1 FROM entries x JOIN accounts a2 ON a2.id = x.accountId WHERE x.transactionId = e.transactionId AND a2.type = 'EQUITY') AS eq,
            EXISTS(SELECT 1 FROM entries x JOIN accounts a5 ON a5.id = x.accountId WHERE x.transactionId = e.transactionId AND a5.type = 'INCOME' AND x.dimensionId = :yieldDimensionId) AS yl
          FROM entries e
          JOIN accounts a ON a.id = e.accountId
          JOIN transactions o ON o.id = e.transactionId
          WHERE a.type = 'ASSET' AND substr(o.date, 1, 7) = :yearMonth
            AND (
              EXISTS(SELECT 1 FROM entries x JOIN accounts a3 ON a3.id = x.accountId WHERE x.transactionId = e.transactionId AND a3.type = 'EQUITY')
              OR EXISTS(SELECT 1 FROM entries x JOIN accounts a4 ON a4.id = x.accountId WHERE x.transactionId = e.transactionId AND a4.type IN ('EXPENSE', 'INCOME'))
            )
        )
        GROUP BY currency
        """
    )
    suspend fun assetMonthTotals(
        yearMonth: String,
        yieldDimensionId: Long?,
    ): List<AssetMonthTotals>

    /**
     * Net worth per currency = Σ ASSET + LIABILITY natural balances (liabilities are
     * stored negative).
     *
     * **CONVERSION stays out, and that is the point.** With the rate at 5.50, a
     * transfer of R$ 550 → US$ 100 leaves `−550 BRL` and `+100 USD` in the user's own
     * accounts, which consolidate to zero: net worth does not move, as it should. If
     * the rate goes to 6.00 the same balances consolidate to `+50` — the gain shows up
     * in the user's own accounts, and including the conversion accounts would count it
     * twice.
     */
    @Query(
        "SELECT e.currency AS currency, COALESCE(SUM(e.amount), 0) AS total FROM entries e " +
            "JOIN accounts a ON a.id = e.accountId " +
            "WHERE a.type IN ('ASSET', 'LIABILITY') " +
            "GROUP BY e.currency"
    )
    suspend fun netWorthCents(): List<CurrencyTotal>

    /**
     * Per-dimension totals of the nominal legs of a given nature in a date range,
     * scoped by perspective: only transactions that also have a leg on one of
     * [siblingAccountIds] (the perspective's asset accounts, or the card's liability
     * account) are counted. This is spending/income "seen from" those accounts,
     * broken down by whatever the legs are classified as.
     *
     * The unclassified legs come back as the `null` group, by the same mechanism —
     * not by a second query and not through a bucket account.
     */
    @Query(
        """
        SELECT e.dimensionId AS dimensionId, e.currency AS currency,
          COALESCE(SUM(e.amount), 0) AS total
        FROM entries e
        JOIN transactions o ON o.id = e.transactionId
        JOIN accounts a ON a.id = e.accountId
        WHERE a.type = :nominalType AND o.date BETWEEN :start AND :end
          AND EXISTS (SELECT 1 FROM entries s WHERE s.transactionId = o.id AND s.accountId IN (:siblingAccountIds))
        GROUP BY e.dimensionId, e.currency
        """
    )
    suspend fun totalsByDimensionWithSiblingLeg(
        nominalType: String,
        start: LocalDate,
        end: LocalDate,
        siblingAccountIds: List<Long>,
    ): List<DimensionCurrencyTotal>

    /**
     * The income/expense/balance/opening-balance a report shows for an account or card
     * scope over [startDate]..[endDate], derived from the ledger. [scopeIds] are the
     * accounts the report is "seen from" — the perspective's ASSET accounts, or a card's
     * single LIABILITY account. Each figure sums the scope legs; income/expense are
     * classified by the transaction's counter-legs (an EQUITY counter-leg is an
     * adjustment, so it lands in [balance] but not in income/expense). Internal
     * transfers — a transaction with two or more ASSET legs all inside [scopeIds] —
     * are excluded from both the period and the opening balance, exactly as the account
     * screen ignores moving money between the user's own accounts. See [ScopeStatsTotals].
     */
    @Query(
        """
        SELECT currency AS currency,
          COALESCE(SUM(CASE WHEN inPeriod = 1 AND eq = 0 AND amount > 0 THEN amount ELSE 0 END), 0) AS income,
          COALESCE(SUM(CASE WHEN inPeriod = 1 AND eq = 0 AND amount < 0 THEN -amount ELSE 0 END), 0) AS expense,
          COALESCE(SUM(CASE WHEN inPeriod = 1 THEN amount ELSE 0 END), 0) AS balance,
          COALESCE(SUM(CASE WHEN inPeriod = 0 THEN amount ELSE 0 END), 0) AS openingBalance
        FROM (
          SELECT e.amount AS amount, e.currency AS currency,
            CASE WHEN o.date >= :startDate THEN 1 ELSE 0 END AS inPeriod,
            EXISTS(SELECT 1 FROM entries x JOIN accounts a ON a.id = x.accountId
                   WHERE x.transactionId = e.transactionId AND a.type = 'EQUITY') AS eq
          FROM entries e
          JOIN transactions o ON o.id = e.transactionId
          WHERE e.accountId IN (:scopeIds)
            AND o.date <= :endDate
            AND NOT (
              (SELECT COUNT(DISTINCT x.accountId) FROM entries x JOIN accounts a ON a.id = x.accountId
               WHERE x.transactionId = e.transactionId AND a.type = 'ASSET') >= 2
              AND NOT EXISTS (
                SELECT 1 FROM entries x JOIN accounts a ON a.id = x.accountId
                WHERE x.transactionId = e.transactionId AND a.type = 'ASSET' AND x.accountId NOT IN (:scopeIds)
              )
            )
        )
        GROUP BY currency
        """
    )
    suspend fun scopeStats(
        scopeIds: List<Long>,
        startDate: LocalDate,
        endDate: LocalDate,
    ): List<ScopeStatsTotals>

    /**
     * Per-dimension totals scoped to a set of sub-ledgers: the nominal legs of
     * transactions that also have a leg tagged with one of [scopeDimensionIds].
     */
    @Query(
        """
        SELECT e.dimensionId AS dimensionId, e.currency AS currency,
          COALESCE(SUM(e.amount), 0) AS total
        FROM entries e
        JOIN accounts a ON a.id = e.accountId
        WHERE a.type = :nominalType
          AND EXISTS (SELECT 1 FROM entries s WHERE s.transactionId = e.transactionId AND s.dimensionId IN (:scopeDimensionIds))
        GROUP BY e.dimensionId, e.currency
        """
    )
    suspend fun totalsByDimensionInScope(
        nominalType: String,
        scopeDimensionIds: List<Long>,
    ): List<DimensionCurrencyTotal>
}
