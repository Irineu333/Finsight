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

/** Aggregated natural balance (cents) of one category/chart account. */
data class CategoryAccountTotal(val accountId: Long, val total: Long)

/**
 * The per-account, per-period money flows (cents) an account screen shows, derived
 * from the ledger and classified by the operation's counter-legs — the ledger
 * equivalent of the legacy `AccountUi` sums:
 *  - [income]/[expense]: operations with neither an EQUITY nor a LIABILITY leg,
 *    split by the sign of the account's own entry (this includes a transfer's two
 *    legs, exactly as the legacy leg types EXPENSE/INCOME did);
 *  - [adjustment]: operations with an EQUITY counter-leg, kept signed;
 *  - [invoicePayment]: operations with a LIABILITY counter-leg (a card payment).
 * All are positive magnitudes except [adjustment], which is signed.
 */
data class AccountPeriodTotals(
    val income: Long,
    val expense: Long,
    val adjustment: Long,
    val invoicePayment: Long,
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

    @Delete
    suspend fun delete(entry: EntryEntity)

    @Query("DELETE FROM entries WHERE operationId = :operationId")
    suspend fun deleteByOperationId(operationId: Long)

    @Query("SELECT * FROM entries ORDER BY id ASC")
    suspend fun getAll(): List<EntryEntity>

    @Query("SELECT * FROM entries ORDER BY id ASC")
    fun observeAll(): Flow<List<EntryEntity>>

    @Query("SELECT * FROM entries WHERE operationId = :operationId ORDER BY id ASC")
    suspend fun getByOperationId(operationId: Long): List<EntryEntity>

    /** Entries of an operation, each hydrated with its account — a complete leg. */
    @Transaction
    @Query("SELECT * FROM entries WHERE operationId = :operationId ORDER BY id ASC")
    suspend fun getEntriesWithAccountByOperationId(operationId: Long): List<EntryWithAccount>

    /** Observes the entries of an operation, each hydrated with its account. */
    @Transaction
    @Query("SELECT * FROM entries WHERE operationId = :operationId ORDER BY id ASC")
    fun observeEntriesWithAccountByOperationId(operationId: Long): Flow<List<EntryWithAccount>>

    @Query("SELECT * FROM entries WHERE accountId = :accountId ORDER BY id ASC")
    fun observeByAccountId(accountId: Long): Flow<List<EntryEntity>>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM entries WHERE accountId = :accountId AND currency = :currency")
    suspend fun naturalBalanceOf(accountId: Long, currency: String): Long

    // --- Ledger reads (natural, debit-positive cents). All derive from Σ amount. ---

    /** Natural balance of an account up to and including the given month (yyyy-MM). */
    @Query(
        "SELECT COALESCE(SUM(e.amount), 0) FROM entries e " +
            "JOIN operations o ON o.id = e.operationId " +
            "WHERE e.accountId = :accountId AND substr(o.date, 1, 7) <= :yearMonth"
    )
    suspend fun balanceUpToMonth(accountId: Long, yearMonth: String): Long

    /** Combined natural balance of every ASSET account up to and including the month. */
    @Query(
        "SELECT COALESCE(SUM(e.amount), 0) FROM entries e " +
            "JOIN operations o ON o.id = e.operationId " +
            "JOIN accounts a ON a.id = e.accountId " +
            "WHERE a.type = 'ASSET' AND substr(o.date, 1, 7) <= :yearMonth"
    )
    suspend fun assetsBalanceUpToMonth(yearMonth: String): Long

    /** Natural balance of an account within a single month (yyyy-MM) — used for category spending. */
    @Query(
        "SELECT COALESCE(SUM(e.amount), 0) FROM entries e " +
            "JOIN operations o ON o.id = e.operationId " +
            "WHERE e.accountId = :accountId AND substr(o.date, 1, 7) = :yearMonth"
    )
    suspend fun balanceInMonth(accountId: Long, yearMonth: String): Long

    /** Natural balance of a card invoice = Σ its liability-leg entries. */
    @Query("SELECT COALESCE(SUM(amount), 0) FROM entries WHERE invoiceId = :invoiceId")
    suspend fun invoiceNaturalBalance(invoiceId: Long): Long

    /**
     * The account's income/expense/adjustment/invoice-payment flows within a month
     * (yyyy-MM), classified by each operation's counter-legs. See [AccountPeriodTotals].
     */
    @Query(
        """
        SELECT
          COALESCE(SUM(CASE WHEN eq = 0 AND li = 0 AND amount > 0 THEN amount ELSE 0 END), 0) AS income,
          COALESCE(SUM(CASE WHEN eq = 0 AND li = 0 AND amount < 0 THEN -amount ELSE 0 END), 0) AS expense,
          COALESCE(SUM(CASE WHEN eq = 1 THEN amount ELSE 0 END), 0) AS adjustment,
          COALESCE(SUM(CASE WHEN eq = 0 AND li = 1 THEN -amount ELSE 0 END), 0) AS invoicePayment
        FROM (
          SELECT e.amount AS amount,
            EXISTS(SELECT 1 FROM entries x JOIN accounts a ON a.id = x.accountId WHERE x.operationId = e.operationId AND a.type = 'EQUITY') AS eq,
            EXISTS(SELECT 1 FROM entries x JOIN accounts a ON a.id = x.accountId WHERE x.operationId = e.operationId AND a.type = 'LIABILITY') AS li
          FROM entries e
          JOIN operations o ON o.id = e.operationId
          WHERE e.accountId = :accountId AND substr(o.date, 1, 7) = :yearMonth
        )
        """
    )
    suspend fun accountPeriodTotals(accountId: Long, yearMonth: String): AccountPeriodTotals

    /** Number of entries on a category (chart) account within a month (yyyy-MM). */
    @Query(
        "SELECT COUNT(*) FROM entries e " +
            "JOIN operations o ON o.id = e.operationId " +
            "WHERE e.accountId = :accountId AND substr(o.date, 1, 7) = :yearMonth"
    )
    suspend fun entryCountInMonth(accountId: Long, yearMonth: String): Int

    /** Net worth = Σ ASSET + LIABILITY natural balances (liabilities are stored negative). */
    @Query(
        "SELECT COALESCE(SUM(e.amount), 0) FROM entries e " +
            "JOIN accounts a ON a.id = e.accountId " +
            "WHERE a.type IN ('ASSET', 'LIABILITY')"
    )
    suspend fun netWorthCents(): Long

    /**
     * Per-category totals in a date range, scoped by perspective: only operations
     * that also have a leg on one of [siblingAccountIds] (the perspective's asset
     * accounts, or the card's liability account) are counted. This is category
     * spending/income "seen from" those accounts.
     */
    @Query(
        """
        SELECT e.accountId AS accountId, COALESCE(SUM(e.amount), 0) AS total
        FROM entries e
        JOIN operations o ON o.id = e.operationId
        JOIN accounts a ON a.id = e.accountId
        WHERE a.type = :categoryType AND o.date BETWEEN :start AND :end
          AND EXISTS (SELECT 1 FROM entries s WHERE s.operationId = o.id AND s.accountId IN (:siblingAccountIds))
        GROUP BY e.accountId
        """
    )
    suspend fun categoryTotalsWithSiblingLeg(
        categoryType: String,
        start: LocalDate,
        end: LocalDate,
        siblingAccountIds: List<Long>,
    ): List<CategoryAccountTotal>

    /**
     * Per-category totals scoped to a set of invoices: category legs of operations
     * that also have a leg tagged with one of [invoiceIds] (the card sub-ledger).
     */
    @Query(
        """
        SELECT e.accountId AS accountId, COALESCE(SUM(e.amount), 0) AS total
        FROM entries e
        JOIN accounts a ON a.id = e.accountId
        WHERE a.type = :categoryType
          AND EXISTS (SELECT 1 FROM entries s WHERE s.operationId = e.operationId AND s.invoiceId IN (:invoiceIds))
        GROUP BY e.accountId
        """
    )
    suspend fun categoryTotalsForInvoices(
        categoryType: String,
        invoiceIds: List<Long>,
    ): List<CategoryAccountTotal>
}
