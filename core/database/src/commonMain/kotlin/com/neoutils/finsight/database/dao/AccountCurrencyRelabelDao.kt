package com.neoutils.finsight.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.neoutils.finsight.database.entity.AccountCurrencyRelabelLogEntity
import com.neoutils.finsight.database.entity.AppMigrationLogEntity

/**
 * The relabelling step, as data: what would be touched, the record of touching it, and the
 * claim that it ran.
 *
 * All three are here rather than spread across the account and migration DAOs because they
 * are only ever used together, inside one transaction — reading the claim, writing the
 * snapshot, applying the update and writing the claim back are one operation that either
 * happens or does not.
 */
@Dao
interface AccountCurrencyRelabelDao {

    /** Whether the step has run. Read inside the transaction that would run it, never before. */
    @Query("SELECT COUNT(*) > 0 FROM app_migration_log WHERE step = :step")
    suspend fun hasRun(step: String): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun markRun(entry: AppMigrationLogEntity)

    /** The accounts still denominated in [currency] — what a relabelling would touch. */
    @Query("SELECT id FROM accounts WHERE currency = :currency")
    suspend fun accountsDenominatedIn(currency: String): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun log(entries: List<AccountCurrencyRelabelLogEntity>)

    /**
     * Relabelling, and only relabelling: the denomination of every row changes together, so
     * no amount moves, no entry is touched and `Σ = 0` per currency keeps holding — the
     * currency every leg is summed under simply has a different name afterwards.
     */
    @Query("UPDATE accounts SET currency = :to WHERE currency = :from")
    suspend fun relabel(from: String, to: String)

    /** What support reads when a user asks why their figures changed denomination. */
    @Query("SELECT * FROM account_currency_relabel_log ORDER BY accountId")
    suspend fun relabelLog(): List<AccountCurrencyRelabelLogEntity>
}
