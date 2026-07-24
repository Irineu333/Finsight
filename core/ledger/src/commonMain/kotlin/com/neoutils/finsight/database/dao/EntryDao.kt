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
 * Aggregated natural balance (cents) of one dimension. A `null` [dimensionId] is
 * the legitimate *unclassified* bucket — entries on a nominal account carrying no
 * dimension — and not an absence of data: it is the same `GROUP BY`, one of whose
 * groups happens to be "none".
 */
data class DimensionTotal(val dimensionId: Long?, val total: Long)

/**
 * The per-account, per-period money flows (cents) an account screen shows, derived
 * from the ledger and classified by the transaction's counter-legs — the ledger
 * equivalent of the legacy `AccountUi` sums:
 *  - [income]/[expense]: transactions with neither an EQUITY nor a LIABILITY leg,
 *    split by the sign of the account's own entry (this includes a transfer's two
 *    legs, exactly as the legacy leg types EXPENSE/INCOME did);
 *  - [adjustment]: transactions with an EQUITY counter-leg, kept signed;
 *  - [settlement]: transactions with a LIABILITY counter-leg — paying off a debt.
 * All are positive magnitudes except [adjustment], which is signed.
 */
data class AccountPeriodTotals(
    val income: Long,
    val expense: Long,
    val adjustment: Long,
    val settlement: Long,
)

/**
 * The money flows (cents) of one sub-ledger, from the entries carrying its
 * dimension. [expense]/[advancePayment] are positive magnitudes; [adjustment] is
 * signed.
 */
data class DimensionPeriodTotals(
    val expense: Long,
    val advancePayment: Long,
    val adjustment: Long,
)

/** [DimensionPeriodTotals] keyed by its dimension, for the batched grouped read. */
data class DimensionPeriodTotalsRow(
    val dimensionId: Long,
    val expense: Long,
    val advancePayment: Long,
    val adjustment: Long,
)

/** Month-wide card [expense]/[payment] (cents), both positive magnitudes. */
data class LiabilityMonthTotals(
    val expense: Long,
    val payment: Long,
)

/**
 * The month-wide income/expense/adjustment (cents) across every ASSET account,
 * classified by each transaction's counter-legs — the "money in / money out" a
 * transaction list or dashboard summarises. Transfers and card payments move money
 * between the user's own accounts and are neither, so they are excluded. [income]/
 * [expense] are positive magnitudes; [adjustment] is signed. See [EntryDao.assetMonthTotals].
 */
data class AssetMonthTotals(
    val income: Long,
    val expense: Long,
    val adjustment: Long,
)

/**
 * The report figures for an account/card scope over a period, all in cents. [income]/
 * [expense] are positive magnitudes of the scope legs classified by counter-leg;
 * [balance] is their signed sum within the period (adjustments included); [openingBalance]
 * is the signed sum of the scope legs before the period. Internal transfers — a
 * transaction whose ASSET legs all fall inside the scope — are excluded on both sides.
 */
data class ScopeStatsTotals(
    val income: Long,
    val expense: Long,
    val balance: Long,
    val openingBalance: Long,
)

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



    /** All-time natural balance of an account, across every date and currency. */
    @Query("SELECT COALESCE(SUM(amount), 0) FROM entries WHERE accountId = :accountId")
    suspend fun balanceOf(accountId: Long): Long

    // --- Ledger reads (natural, debit-positive cents). All derive from Σ amount. ---

    /** Natural balance of an account up to and including the given month (yyyy-MM). */
    @Query(
        "SELECT COALESCE(SUM(e.amount), 0) FROM entries e " +
            "JOIN transactions o ON o.id = e.transactionId " +
            "WHERE e.accountId = :accountId AND substr(o.date, 1, 7) <= :yearMonth"
    )
    suspend fun balanceUpToMonth(accountId: Long, yearMonth: String): Long

    /** Combined natural balance of every ASSET account up to and including the month. */
    @Query(
        "SELECT COALESCE(SUM(e.amount), 0) FROM entries e " +
            "JOIN transactions o ON o.id = e.transactionId " +
            "JOIN accounts a ON a.id = e.accountId " +
            "WHERE a.type = 'ASSET' AND substr(o.date, 1, 7) <= :yearMonth"
    )
    suspend fun assetsBalanceUpToMonth(yearMonth: String): Long

    /** Natural balance of a dimension within a single month (yyyy-MM). */
    @Query(
        "SELECT COALESCE(SUM(e.amount), 0) FROM entries e " +
            "JOIN transactions o ON o.id = e.transactionId " +
            "WHERE e.dimensionId = :dimensionId AND substr(o.date, 1, 7) = :yearMonth"
    )
    suspend fun dimensionBalanceInMonth(dimensionId: Long, yearMonth: String): Long

    /** Natural balance of a sub-ledger = Σ the entries tagged with its dimension. */
    @Query("SELECT COALESCE(SUM(amount), 0) FROM entries WHERE dimensionId = :dimensionId")
    suspend fun dimensionNaturalBalance(dimensionId: Long): Long

    /**
     * The natural balance of each dimension in [dimensionIds], in one grouped query,
     * so a screen listing many sub-ledgers reads them all at once instead of one
     * query per dimension. A dimension with no entries is simply absent from the
     * result (its balance is 0).
     */
    @Query(
        "SELECT dimensionId AS dimensionId, COALESCE(SUM(amount), 0) AS total " +
            "FROM entries WHERE dimensionId IN (:dimensionIds) GROUP BY dimensionId"
    )
    suspend fun naturalBalanceByDimension(dimensionIds: List<Long>): List<DimensionTotal>

    /**
     * The account's income/expense/adjustment/invoice-payment flows within a month
     * (yyyy-MM), classified by each transaction's counter-legs. See [AccountPeriodTotals].
     */
    @Query(
        """
        SELECT
          COALESCE(SUM(CASE WHEN eq = 0 AND li = 0 AND amount > 0 THEN amount ELSE 0 END), 0) AS income,
          COALESCE(SUM(CASE WHEN eq = 0 AND li = 0 AND amount < 0 THEN -amount ELSE 0 END), 0) AS expense,
          COALESCE(SUM(CASE WHEN eq = 1 THEN amount ELSE 0 END), 0) AS adjustment,
          COALESCE(SUM(CASE WHEN eq = 0 AND li = 1 THEN -amount ELSE 0 END), 0) AS settlement
        FROM (
          SELECT e.amount AS amount,
            EXISTS(SELECT 1 FROM entries x JOIN accounts a ON a.id = x.accountId WHERE x.transactionId = e.transactionId AND a.type = 'EQUITY') AS eq,
            EXISTS(SELECT 1 FROM entries x JOIN accounts a ON a.id = x.accountId WHERE x.transactionId = e.transactionId AND a.type = 'LIABILITY') AS li
          FROM entries e
          JOIN transactions o ON o.id = e.transactionId
          WHERE e.accountId = :accountId AND substr(o.date, 1, 7) = :yearMonth
        )
        """
    )
    suspend fun accountPeriodTotals(accountId: Long, yearMonth: String): AccountPeriodTotals

    /**
     * The expense/advance-payment/adjustment breakdown of a sub-ledger, from the
     * entries tagged with its dimension, classified by sign and by whether the
     * transaction also has an EQUITY counter-leg. See [DimensionPeriodTotals]. All are
     * positive magnitudes except [adjustment], which is signed.
     */
    @Query(
        """
        SELECT
          COALESCE(SUM(CASE WHEN eq = 0 AND amount < 0 THEN -amount ELSE 0 END), 0) AS expense,
          COALESCE(SUM(CASE WHEN eq = 0 AND amount > 0 THEN amount ELSE 0 END), 0) AS advancePayment,
          COALESCE(SUM(CASE WHEN eq = 1 THEN amount ELSE 0 END), 0) AS adjustment
        FROM (
          SELECT e.amount AS amount,
            EXISTS(SELECT 1 FROM entries x JOIN accounts a ON a.id = x.accountId WHERE x.transactionId = e.transactionId AND a.type = 'EQUITY') AS eq
          FROM entries e
          WHERE e.dimensionId = :dimensionId
        )
        """
    )
    suspend fun dimensionPeriodTotals(dimensionId: Long): DimensionPeriodTotals

    /**
     * The same expense/advance-payment/adjustment breakdown as [dimensionPeriodTotals],
     * but for every dimension in [dimensionIds] at once — a single grouped read for a
     * screen showing many sub-ledgers. A dimension with no entries is absent from the
     * result (all its flows are 0).
     */
    @Query(
        """
        SELECT dimensionId AS dimensionId,
          COALESCE(SUM(CASE WHEN eq = 0 AND amount < 0 THEN -amount ELSE 0 END), 0) AS expense,
          COALESCE(SUM(CASE WHEN eq = 0 AND amount > 0 THEN amount ELSE 0 END), 0) AS advancePayment,
          COALESCE(SUM(CASE WHEN eq = 1 THEN amount ELSE 0 END), 0) AS adjustment
        FROM (
          SELECT e.dimensionId AS dimensionId, e.amount AS amount,
            EXISTS(SELECT 1 FROM entries x JOIN accounts a ON a.id = x.accountId WHERE x.transactionId = e.transactionId AND a.type = 'EQUITY') AS eq
          FROM entries e
          WHERE e.dimensionId IN (:dimensionIds)
        )
        GROUP BY dimensionId
        """
    )
    suspend fun periodTotalsByDimension(dimensionIds: List<Long>): List<DimensionPeriodTotalsRow>

    /**
     * Month-wide card expense/advance-payment across every LIABILITY (card) account
     * (yyyy-MM), classified by sign and EQUITY presence. Both positive magnitudes.
     */
    @Query(
        """
        SELECT
          COALESCE(SUM(CASE WHEN eq = 0 AND amount < 0 THEN -amount ELSE 0 END), 0) AS expense,
          COALESCE(SUM(CASE WHEN eq = 0 AND amount > 0 THEN amount ELSE 0 END), 0) AS payment
        FROM (
          SELECT e.amount AS amount,
            EXISTS(SELECT 1 FROM entries x JOIN accounts a2 ON a2.id = x.accountId WHERE x.transactionId = e.transactionId AND a2.type = 'EQUITY') AS eq
          FROM entries e
          JOIN accounts a ON a.id = e.accountId
          JOIN transactions o ON o.id = e.transactionId
          WHERE a.type = 'LIABILITY' AND substr(o.date, 1, 7) = :yearMonth
        )
        """
    )
    suspend fun liabilityMonthTotals(yearMonth: String): LiabilityMonthTotals

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
        SELECT
          COALESCE(SUM(CASE WHEN eq = 0 AND amount > 0 THEN amount ELSE 0 END), 0) AS income,
          COALESCE(SUM(CASE WHEN eq = 0 AND amount < 0 THEN -amount ELSE 0 END), 0) AS expense,
          COALESCE(SUM(CASE WHEN eq = 1 THEN amount ELSE 0 END), 0) AS adjustment
        FROM (
          SELECT e.amount AS amount,
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
        """
    )
    suspend fun assetMonthTotals(yearMonth: String): AssetMonthTotals

    /** Number of entries carrying a dimension within a month (yyyy-MM). */
    @Query(
        "SELECT COUNT(*) FROM entries e " +
            "JOIN transactions o ON o.id = e.transactionId " +
            "WHERE e.dimensionId = :dimensionId AND substr(o.date, 1, 7) = :yearMonth"
    )
    suspend fun dimensionEntryCountInMonth(dimensionId: Long, yearMonth: String): Int

    /** Net worth = Σ ASSET + LIABILITY natural balances (liabilities are stored negative). */
    @Query(
        "SELECT COALESCE(SUM(e.amount), 0) FROM entries e " +
            "JOIN accounts a ON a.id = e.accountId " +
            "WHERE a.type IN ('ASSET', 'LIABILITY')"
    )
    suspend fun netWorthCents(): Long

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
        SELECT e.dimensionId AS dimensionId, COALESCE(SUM(e.amount), 0) AS total
        FROM entries e
        JOIN transactions o ON o.id = e.transactionId
        JOIN accounts a ON a.id = e.accountId
        WHERE a.type = :nominalType AND o.date BETWEEN :start AND :end
          AND EXISTS (SELECT 1 FROM entries s WHERE s.transactionId = o.id AND s.accountId IN (:siblingAccountIds))
        GROUP BY e.dimensionId
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
        SELECT
          COALESCE(SUM(CASE WHEN inPeriod = 1 AND eq = 0 AND amount > 0 THEN amount ELSE 0 END), 0) AS income,
          COALESCE(SUM(CASE WHEN inPeriod = 1 AND eq = 0 AND amount < 0 THEN -amount ELSE 0 END), 0) AS expense,
          COALESCE(SUM(CASE WHEN inPeriod = 1 THEN amount ELSE 0 END), 0) AS balance,
          COALESCE(SUM(CASE WHEN inPeriod = 0 THEN amount ELSE 0 END), 0) AS openingBalance
        FROM (
          SELECT e.amount AS amount,
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
        """
    )
    suspend fun scopeStats(
        scopeIds: List<Long>,
        startDate: LocalDate,
        endDate: LocalDate,
    ): ScopeStatsTotals

    /**
     * Per-dimension totals scoped to a set of sub-ledgers: the nominal legs of
     * transactions that also have a leg tagged with one of [scopeDimensionIds].
     */
    @Query(
        """
        SELECT e.dimensionId AS dimensionId, COALESCE(SUM(e.amount), 0) AS total
        FROM entries e
        JOIN accounts a ON a.id = e.accountId
        WHERE a.type = :nominalType
          AND EXISTS (SELECT 1 FROM entries s WHERE s.transactionId = e.transactionId AND s.dimensionId IN (:scopeDimensionIds))
        GROUP BY e.dimensionId
        """
    )
    suspend fun totalsByDimensionInScope(
        nominalType: String,
        scopeDimensionIds: List<Long>,
    ): List<DimensionTotal>
}
