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
 * One row of an aggregate grouped by currency. It exists so the repository has a single
 * way of folding grouped rows into a per-currency figure, whatever the row's other columns
 * are — without it, each aggregate would key by currency in its own words.
 */
interface CurrencyGrouped {
    val currency: String
}

/**
 * One currency's share (cents) of an aggregate that could span accounts of different
 * currencies. Every such read comes back as a list of these — one row per currency
 * present — because the ledger never adds two currencies into one number.
 */
data class CurrencyTotal(override val currency: String, val total: Long) : CurrencyGrouped

/**
 * Aggregated natural balance (cents) of one dimension, in one [currency]. A `null`
 * [dimensionId] is the legitimate *unclassified* bucket — entries on a nominal account
 * carrying no dimension — and not an absence of data: it is the same `GROUP BY`, one of
 * whose groups happens to be "none".
 *
 * A dimension is not bound to a single account, so its entries may be denominated in
 * several currencies: the group is `(dimension, currency)`, never the dimension alone.
 * The ledger does not consult `DimensionKind` to decide otherwise — that a card invoice
 * always lands on one card is the card facade's guarantee, not the ledger's.
 */
data class DimensionTotal(
    val dimensionId: Long?,
    override val currency: String,
    val total: Long,
) : CurrencyGrouped

/**
 * The per-account, per-period money flows (cents) an account screen shows, derived
 * from the ledger and classified by the transaction's counter-legs, all denominated in
 * the account's own [currency] — the ledger equivalent of the legacy `AccountUi` sums:
 *  - [income]/[expense]: transactions with neither an EQUITY nor a LIABILITY leg,
 *    split by the sign of the account's own entry (this includes a transfer's two
 *    legs, exactly as the legacy leg types EXPENSE/INCOME did);
 *  - [adjustment]: transactions with an EQUITY counter-leg, kept signed;
 *  - [settlement]: transactions with a LIABILITY counter-leg — paying off a debt.
 * All are positive magnitudes except [adjustment], which is signed.
 */
data class AccountPeriodTotals(
    override val currency: String,
    val income: Long,
    val expense: Long,
    val adjustment: Long,
    val settlement: Long,
) : CurrencyGrouped

/**
 * The money flows (cents) of one sub-ledger in one [currency], from the entries carrying
 * its dimension. [expense]/[advancePayment] are positive magnitudes; [adjustment] is
 * signed.
 */
data class DimensionPeriodTotals(
    override val currency: String,
    val expense: Long,
    val advancePayment: Long,
    val adjustment: Long,
) : CurrencyGrouped

/** [DimensionPeriodTotals] keyed by its dimension, for the batched grouped read. */
data class DimensionPeriodTotalsRow(
    val dimensionId: Long,
    override val currency: String,
    val expense: Long,
    val advancePayment: Long,
    val adjustment: Long,
) : CurrencyGrouped

/**
 * Month-wide card [expense]/[payment] (cents) in one [currency], both positive magnitudes,
 * plus the signed [adjustment] — the EQUITY counter-leg — in symmetry with [AssetMonthTotals].
 */
data class LiabilityMonthTotals(
    override val currency: String,
    val expense: Long,
    val payment: Long,
    val adjustment: Long,
) : CurrencyGrouped

/**
 * The month-wide income/expense/adjustment (cents) across every ASSET account,
 * classified by each transaction's counter-legs — the "money in / money out" a
 * transaction list or dashboard summarises. Transfers and card payments move money
 * between the user's own accounts and are neither, so they are excluded. [income]/
 * [expense] are positive magnitudes; [adjustment] is signed, all in one [currency].
 * See [EntryDao.assetMonthTotals].
 */
data class AssetMonthTotals(
    override val currency: String,
    val income: Long,
    val expense: Long,
    val adjustment: Long,
) : CurrencyGrouped

/**
 * The report figures for an account/card scope over a period, all in cents. [income]/
 * [expense] are positive magnitudes of the scope legs classified by counter-leg;
 * [balance] is their signed sum within the period (adjustments included); [openingBalance]
 * is the signed sum of the scope legs before the period. Internal transfers — a
 * transaction whose ASSET legs all fall inside the scope — are excluded on both sides.
 * Every figure is denominated in [currency]: a scope may hold accounts of several.
 */
data class ScopeStatsTotals(
    override val currency: String,
    val income: Long,
    val expense: Long,
    val balance: Long,
    val openingBalance: Long,
) : CurrencyGrouped

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

    /** Entries of a transaction, each hydrated with its account — a complete leg. */
    @Transaction
    @Query("SELECT * FROM entries WHERE transactionId = :transactionId ORDER BY id ASC")
    suspend fun getEntriesWithAccountByTransactionId(transactionId: Long): List<EntryWithAccount>

    /** Observes the entries of a transaction, each hydrated with its account. */
    @Transaction
    @Query("SELECT * FROM entries WHERE transactionId = :transactionId ORDER BY id ASC")
    fun observeEntriesWithAccountByTransactionId(transactionId: Long): Flow<List<EntryWithAccount>>



    /**
     * All-time natural balance of an account, across every date. It stays a single number
     * because it is scoped to one account, and currency is an attribute of the account —
     * which is why the read starts from the account row: an account with no entry at all
     * still has a currency, and one that is not in the chart has no row and no answer.
     */
    @Query(
        "SELECT a.currency AS currency, " +
            "COALESCE((SELECT SUM(e.amount) FROM entries e WHERE e.accountId = a.id), 0) AS total " +
            "FROM accounts a WHERE a.id = :accountId"
    )
    suspend fun balanceOf(accountId: Long): CurrencyTotal?

    // --- Ledger reads (natural, debit-positive cents). All derive from Σ amount. ---

    /** Natural balance of an account up to and including the given month (yyyy-MM). */
    @Query(
        "SELECT a.currency AS currency, COALESCE((" +
            "SELECT SUM(e.amount) FROM entries e " +
            "JOIN transactions o ON o.id = e.transactionId " +
            "WHERE e.accountId = a.id AND substr(o.date, 1, 7) <= :yearMonth" +
            "), 0) AS total " +
            "FROM accounts a WHERE a.id = :accountId"
    )
    suspend fun balanceUpToMonth(accountId: Long, yearMonth: String): CurrencyTotal?

    /**
     * Combined natural balance of every account of one nature up to and including the
     * month, **per currency**. The nature is a parameter, not a literal, so the same
     * aggregate serves ASSET and LIABILITY — and their consolidated figure is the sum of
     * two calls, since liabilities are stored in credit.
     */
    @Query(
        "SELECT e.currency AS currency, COALESCE(SUM(e.amount), 0) AS total FROM entries e " +
            "JOIN transactions o ON o.id = e.transactionId " +
            "JOIN accounts a ON a.id = e.accountId " +
            "WHERE a.type = :type AND substr(o.date, 1, 7) <= :yearMonth " +
            "GROUP BY e.currency"
    )
    suspend fun balanceUpToMonthByType(type: String, yearMonth: String): List<CurrencyTotal>

    /** Natural balance of a dimension within a single month (yyyy-MM), per currency. */
    @Query(
        "SELECT e.currency AS currency, COALESCE(SUM(e.amount), 0) AS total FROM entries e " +
            "JOIN transactions o ON o.id = e.transactionId " +
            "WHERE e.dimensionId = :dimensionId AND substr(o.date, 1, 7) = :yearMonth " +
            "GROUP BY e.currency"
    )
    suspend fun dimensionBalanceInMonth(dimensionId: Long, yearMonth: String): List<CurrencyTotal>

    /**
     * Natural balance of a sub-ledger = Σ the entries tagged with its dimension, per
     * currency. Nothing binds a dimension to one account, so the read is grouped whatever
     * the dimension is — a facade that knows its own is single-currency reduces it itself.
     */
    @Query(
        "SELECT currency AS currency, COALESCE(SUM(amount), 0) AS total " +
            "FROM entries WHERE dimensionId = :dimensionId GROUP BY currency"
    )
    suspend fun dimensionNaturalBalance(dimensionId: Long): List<CurrencyTotal>

    /**
     * The natural balance of each dimension in [dimensionIds], per currency, in one
     * grouped query, so a screen listing many sub-ledgers reads them all at once instead
     * of one query per dimension. A dimension with no entries is simply absent from the
     * result (its balance is 0).
     */
    @Query(
        "SELECT dimensionId AS dimensionId, currency AS currency, COALESCE(SUM(amount), 0) AS total " +
            "FROM entries WHERE dimensionId IN (:dimensionIds) GROUP BY dimensionId, currency"
    )
    suspend fun naturalBalanceByDimension(dimensionIds: List<Long>): List<DimensionTotal>

    /**
     * The account's income/expense/adjustment/invoice-payment flows within a month
     * (yyyy-MM), classified by each transaction's counter-legs. See [AccountPeriodTotals].
     */
    @Query(
        """
        SELECT acc.currency AS currency,
          COALESCE(SUM(CASE WHEN eq = 0 AND li = 0 AND amount > 0 THEN amount ELSE 0 END), 0) AS income,
          COALESCE(SUM(CASE WHEN eq = 0 AND li = 0 AND amount < 0 THEN -amount ELSE 0 END), 0) AS expense,
          COALESCE(SUM(CASE WHEN eq = 1 THEN amount ELSE 0 END), 0) AS adjustment,
          COALESCE(SUM(CASE WHEN eq = 0 AND li = 1 THEN -amount ELSE 0 END), 0) AS settlement
        FROM accounts acc
        LEFT JOIN (
          SELECT e.accountId AS accountId, e.amount AS amount,
            EXISTS(SELECT 1 FROM entries x JOIN accounts a ON a.id = x.accountId WHERE x.transactionId = e.transactionId AND a.type = 'EQUITY') AS eq,
            EXISTS(SELECT 1 FROM entries x JOIN accounts a ON a.id = x.accountId WHERE x.transactionId = e.transactionId AND a.type = 'LIABILITY') AS li
          FROM entries e
          JOIN transactions o ON o.id = e.transactionId
          WHERE e.accountId = :accountId AND substr(o.date, 1, 7) = :yearMonth
        ) ON accountId = acc.id
        WHERE acc.id = :accountId
        GROUP BY acc.currency
        """
    )
    suspend fun accountPeriodTotals(accountId: Long, yearMonth: String): AccountPeriodTotals?

    /**
     * The expense/advance-payment/adjustment breakdown of a sub-ledger, from the
     * entries tagged with its dimension, classified by sign and by whether the
     * transaction also has an EQUITY counter-leg, one row per currency. See
     * [DimensionPeriodTotals]. All are positive magnitudes except [adjustment], which is
     * signed.
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
     * so a refund on an expense reads as income by the same rule. See [AssetMonthTotals].
     */
    @Query(
        """
        SELECT currency AS currency,
          COALESCE(SUM(CASE WHEN eq = 0 AND amount > 0 THEN amount ELSE 0 END), 0) AS income,
          COALESCE(SUM(CASE WHEN eq = 0 AND amount < 0 THEN -amount ELSE 0 END), 0) AS expense,
          COALESCE(SUM(CASE WHEN eq = 1 THEN amount ELSE 0 END), 0) AS adjustment
        FROM (
          SELECT e.amount AS amount, e.currency AS currency,
            EXISTS(SELECT 1 FROM entries x JOIN accounts a2 ON a2.id = x.accountId WHERE x.transactionId = e.transactionId AND a2.type = 'EQUITY') AS eq
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
    suspend fun assetMonthTotals(yearMonth: String): List<AssetMonthTotals>

    /** Number of entries carrying a dimension within a month (yyyy-MM). */
    @Query(
        "SELECT COUNT(*) FROM entries e " +
            "JOIN transactions o ON o.id = e.transactionId " +
            "WHERE e.dimensionId = :dimensionId AND substr(o.date, 1, 7) = :yearMonth"
    )
    suspend fun dimensionEntryCountInMonth(dimensionId: Long, yearMonth: String): Int

    /**
     * Net worth = Σ ASSET + LIABILITY natural balances (liabilities are stored negative),
     * per currency. CONVERSION accounts stay out on purpose: the exchange outcome already
     * shows up in the user's own account balances once they are expressed in one currency,
     * so counting the conversion leg too would count it twice.
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
        SELECT e.dimensionId AS dimensionId, e.currency AS currency, COALESCE(SUM(e.amount), 0) AS total
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
    ): List<DimensionTotal>

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
        SELECT e.dimensionId AS dimensionId, e.currency AS currency, COALESCE(SUM(e.amount), 0) AS total
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
    ): List<DimensionTotal>
}
