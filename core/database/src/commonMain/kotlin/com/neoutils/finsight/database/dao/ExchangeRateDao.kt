package com.neoutils.finsight.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.neoutils.finsight.database.entity.ExchangeRateEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

@Dao
interface ExchangeRateDao {

    /**
     * Records a rate, replacing the one it keys against. Collecting the rate of an operation
     * that is being edited must not leave two rows of the same origin for the same day.
     */
    @Upsert
    suspend fun upsert(rate: ExchangeRateEntity)

    /**
     * The rate that governs a figure dated [date]: the **last one on or before** it, with
     * the user's own winning over a collected one on the same day.
     *
     * "On or before" is the deterministic policy Beancount and Firefly both settle on, and
     * it is what keeps a past figure still: a rate recorded later never reaches back and
     * changes a month that is already closed.
     */
    @Query(
        """
        SELECT * FROM exchange_rates
        WHERE currency = :currency AND date <= :date
        ORDER BY date DESC, CASE source WHEN 'USER' THEN 0 ELSE 1 END
        LIMIT 1
        """
    )
    suspend fun rateOn(currency: String, date: LocalDate): ExchangeRateEntity?

    /**
     * Removes one rate.
     *
     * It is the obligatory corollary of a rate outliving the operation that collected it: a
     * rate gathered from an operation the user has since deleted would otherwise have no
     * path that reaches it. Correcting is not enough — a rate collected by mistake on a day
     * no other rate covers has to be able to stop existing, rather than be replaced by a
     * guess. GnuCash ships a Price Editor for the same reason.
     */
    @Delete
    suspend fun delete(rate: ExchangeRateEntity)

    /** Every rate, newest first — what the rates screen lists and reacts to. */
    @Query("SELECT * FROM exchange_rates ORDER BY date DESC, currency ASC")
    fun observeAll(): Flow<List<ExchangeRateEntity>>

    @Query("SELECT * FROM exchange_rates ORDER BY date DESC, currency ASC")
    suspend fun getAll(): List<ExchangeRateEntity>
}
