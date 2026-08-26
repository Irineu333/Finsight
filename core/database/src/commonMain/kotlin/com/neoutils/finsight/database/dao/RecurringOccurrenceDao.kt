package com.neoutils.finsight.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.neoutils.finsight.database.entity.RecurringOccurrenceEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.YearMonth

/**
 * What the confirmed cycles of one month actually put in the ledger, in one currency
 * (cents), split by the nature of the nominal leg they landed on.
 *
 * Both figures are **magnitudes**: an `EXPENSE` account is debit-natured and holds the
 * amount positive, an `INCOME` one is credit-natured and holds it negative, so the
 * income column negates and the two read the same way round. A refund posted against a
 * recurring expense is a negative `EXPENSE` leg and therefore lowers the expense
 * magnitude, which is the same arithmetic every other month flow of the app does.
 *
 * One row per currency present, and **no row at all** when nothing matched: a grouped
 * aggregate has no empty row, and that is a different fact from a row of zeros.
 */
data class RecurringSettledTotals(
    override val currency: String,
    val expense: Long,
    val income: Long,
) : CurrencyScoped

@Dao
interface RecurringOccurrenceDao {

    /**
     * The money the confirmed cycles of [yearMonth] wrote into the ledger, per currency
     * and per nature.
     *
     * **It reads the transaction through the real foreign key**, `transactionId`, and
     * never through `transactions.recurringId` — that column is grouping metadata whose
     * own entity states that no ledger read consults it. The path here is
     * `recurring_occurrences → transactions → entries → accounts`, which is the ordinary
     * ledger read with an occurrence in front of it. A facade's DAO may write it because
     * the facade owns `recurring_occurrences`; a ledger DAO could not name that table at
     * all.
     *
     * **The month is the occurrence's, not the transaction's date** (design D7). The
     * rule that a recurring transaction may not change month is declared and mapped to a
     * string, and nothing in the app produces it — so a confirmed transaction can still
     * be edited into another month. Cutting by `yearMonth` is what keeps the money summed
     * and the cycle counted from ever disagreeing about which month a cycle belongs to.
     *
     * A skipped cycle has no transaction and no entry, so it contributes to neither
     * column by construction rather than by a clause.
     */
    @Query(
        """
        SELECT e.currency AS currency,
          COALESCE(SUM(CASE WHEN a.type = 'EXPENSE' THEN e.amount ELSE 0 END), 0) AS expense,
          COALESCE(SUM(CASE WHEN a.type = 'INCOME' THEN -e.amount ELSE 0 END), 0) AS income
        FROM recurring_occurrences o
        JOIN transactions t ON t.id = o.transactionId
        JOIN entries e ON e.transactionId = t.id
        JOIN accounts a ON a.id = e.accountId
        WHERE o.yearMonth = :yearMonth
          AND o.status = 'CONFIRMED'
          AND a.type IN ('EXPENSE', 'INCOME')
        GROUP BY e.currency
        """
    )
    suspend fun settledTotalsIn(yearMonth: YearMonth): List<RecurringSettledTotals>

    @Query("SELECT * FROM recurring_occurrences")
    fun observeAll(): Flow<List<RecurringOccurrenceEntity>>

    @Query("SELECT * FROM recurring_occurrences")
    suspend fun getAll(): List<RecurringOccurrenceEntity>

    @Query(
        """
        SELECT * FROM recurring_occurrences
        WHERE recurringId = :recurringId AND yearMonth = :yearMonth
        LIMIT 1
        """
    )
    suspend fun getByRecurringAndMonth(
        recurringId: Long,
        yearMonth: YearMonth,
    ): RecurringOccurrenceEntity?

    @Query(
        """
        SELECT * FROM recurring_occurrences
        WHERE recurringId = :recurringId AND cycleNumber = :cycleNumber
        LIMIT 1
        """
    )
    suspend fun getByRecurringAndCycle(
        recurringId: Long,
        cycleNumber: Int,
    ): RecurringOccurrenceEntity?

    @Insert
    suspend fun insert(entity: RecurringOccurrenceEntity): Long

    @Update
    suspend fun update(entity: RecurringOccurrenceEntity)
}
