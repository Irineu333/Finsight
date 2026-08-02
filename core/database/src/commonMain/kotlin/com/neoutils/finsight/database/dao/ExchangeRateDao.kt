package com.neoutils.finsight.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.neoutils.finsight.database.entity.ExchangeRateEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

/**
 * The archive of exchange rates: the observations that touch a currency on a day.
 *
 * The reading policy is one sentence and it is implemented once, here: **the last
 * observation on or before the date, per pair, with the user's own winning over a
 * derived one of the same date.** Every consolidated figure asks this question and no
 * consumer re-answers it.
 *
 * *Per pair* is what the policy is partitioned by, and not per currency: the dollar
 * against the real and the dollar against the euro are two observations, and collapsing
 * them would let one answer a question it never spoke about.
 *
 * Which of several **paths** between two currencies wins is a different question, and it
 * is not this one's: it belongs above, over what these queries return.
 */
@Dao
interface ExchangeRateDao {

    /**
     * The observation in force for the exact pair `([currency], [counterCurrency])` as
     * of [date] — the latest one not after it, preferring the user's over the derived
     * one when both share that date.
     *
     * This is the **direct** level of the resolution and nothing more: no inverse and no
     * pivot, which are decisions above this one. `null` here does not mean there is no
     * rate — it means there is no rate *of this pair*, which is a different sentence.
     */
    @Query(
        "SELECT * FROM exchange_rates " +
            "WHERE currency = :currency AND counterCurrency = :counterCurrency AND date <= :date " +
            "ORDER BY date DESC, CASE source WHEN 'USER' THEN 0 ELSE 1 END ASC " +
            "LIMIT 1"
    )
    suspend fun rateOfPairAsOf(
        currency: String,
        counterCurrency: String,
        date: LocalDate,
    ): ExchangeRateEntity?

    /**
     * The same question for every currency at once — one row per currency that has any
     * rate on or before [date], each already resolved by the policy above.
     *
     * A consolidated figure spans several currencies, so asking per currency would cost
     * one query per term. The `NOT EXISTS` is the policy stated as a predicate: a row
     * survives when no other row of the **same pair** beats it, either by being later
     * or by being the user's on the same day.
     */
    @Query(
        """
        SELECT * FROM exchange_rates e
        WHERE e.date <= :date
          AND NOT EXISTS (
            SELECT 1 FROM exchange_rates x
            WHERE x.currency = e.currency AND x.counterCurrency = e.counterCurrency
              AND x.date <= :date
              AND (
                x.date > e.date
                OR (x.date = e.date AND x.source = 'USER' AND e.source <> 'USER')
              )
          )
        """
    )
    suspend fun ratesAsOf(date: LocalDate): List<ExchangeRateEntity>

    /**
     * The whole archive, newest first — what the rates screen lists, and the signal a
     * consolidated figure recomputes on. Registering or removing a rate writes no
     * entry, so without a `Flow` over this table nothing on screen would notice.
     */
    @Query("SELECT * FROM exchange_rates ORDER BY date DESC, currency ASC")
    fun observeAll(): Flow<List<ExchangeRateEntity>>

    @Query("SELECT * FROM exchange_rates WHERE currency = :currency ORDER BY date DESC")
    suspend fun getByCurrency(currency: String): List<ExchangeRateEntity>

    /**
     * Registers a rate. `REPLACE` on conflict, and the unique triple is what makes it
     * safe: a second cross-currency operation on a day already observed replaces that
     * day's *derived* rate — the newer observation is the better one — and leaves the
     * user's correction, a different row, untouched.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rate: ExchangeRateEntity): Long

    @Update
    suspend fun update(rate: ExchangeRateEntity)

    /**
     * Removes a rate — the obligatory corollary of a rate surviving its transaction
     * (design D27), not a convenience. A rate observed by mistake from an operation
     * that has since been deleted has no other path that reaches it, and correcting is
     * not enough: it has to be able to stop existing rather than be replaced by a
     * guess.
     */
    @Delete
    suspend fun delete(rate: ExchangeRateEntity)

    @Query("DELETE FROM exchange_rates WHERE id = :id")
    suspend fun deleteById(id: Long)
}
