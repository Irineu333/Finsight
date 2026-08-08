package com.neoutils.finsight.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.neoutils.finsight.database.entity.CurrencyEntity
import kotlinx.coroutines.flow.Flow

// What a form may offer. Archiving answers "stop offering me this", so it is filtered
// here and nowhere else — the ledger has no equivalent filter to apply.
private const val OFFERED_CURRENCIES = "SELECT * FROM currencies WHERE isArchived = 0"

// The registry screen lists the archived ones too, marked: they remain readable
// wherever they are already used, and unarchiving has to be reachable.
private const val ALL_CURRENCIES = "SELECT * FROM currencies"

@Dao
interface CurrencyDao {

    @Query(ALL_CURRENCIES + " ORDER BY code ASC")
    fun observeAll(): Flow<List<CurrencyEntity>>

    @Query(OFFERED_CURRENCIES + " ORDER BY code ASC")
    fun observeOffered(): Flow<List<CurrencyEntity>>

    @Query(ALL_CURRENCIES + " ORDER BY code ASC")
    suspend fun getAll(): List<CurrencyEntity>

    @Query(OFFERED_CURRENCIES + " ORDER BY code ASC")
    suspend fun getOffered(): List<CurrencyEntity>

    @Query("SELECT * FROM currencies WHERE code = :code")
    suspend fun getByCode(code: String): CurrencyEntity?

    @Query("SELECT * FROM currencies WHERE code = :code")
    fun observeByCode(code: String): Flow<CurrencyEntity?>

    @Query("SELECT EXISTS(SELECT 1 FROM currencies WHERE code = :code)")
    suspend fun exists(code: String): Boolean

    /**
     * Registering and editing are the same write: the code is the primary key and it is
     * never edited — renaming it would be a data migration over accounts, entries,
     * budgets and rates, not an edit.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(currency: CurrencyEntity)

    @Query("UPDATE currencies SET isArchived = 1 WHERE code = :code")
    suspend fun archive(code: String)

    @Query("UPDATE currencies SET isArchived = 0 WHERE code = :code")
    suspend fun unarchive(code: String)

    @Query("DELETE FROM currencies WHERE code = :code")
    suspend fun deleteByCode(code: String)
}
