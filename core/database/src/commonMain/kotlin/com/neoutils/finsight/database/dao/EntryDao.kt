package com.neoutils.finsight.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.neoutils.finsight.database.entity.EntryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

/** Aggregated natural balance (cents) of one category/chart account. */
data class CategoryAccountTotal(val accountId: Long, val total: Long)

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
}
