package com.neoutils.finsight.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.neoutils.finsight.database.entity.AccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    // Only ASSET rows are user-facing "accounts"; INCOME/EXPENSE/LIABILITY/EQUITY
    // rows in the same chart-of-accounts table back categories, cards and system
    // reconciliation and must not leak into the accounts facade.
    @Query("SELECT * FROM accounts WHERE type = 'ASSET' ORDER BY createdAt ASC")
    fun observeAllAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE type = 'ASSET' ORDER BY createdAt ASC")
    suspend fun getAllAccounts(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getAccountById(id: Long): AccountEntity?

    // Ledger account lookups (chart of accounts includes non-ASSET rows).
    @Query("SELECT * FROM accounts WHERE type = :type AND name = :name LIMIT 1")
    suspend fun getByTypeAndName(type: AccountEntity.Type, name: String): AccountEntity?

    @Query("SELECT * FROM accounts WHERE id = :id")
    fun observeAccountById(id: Long): Flow<AccountEntity?>

    @Query("SELECT * FROM accounts WHERE type = 'ASSET' AND isDefault = 1 LIMIT 1")
    suspend fun getDefaultAccount(): AccountEntity?

    @Query("SELECT * FROM accounts WHERE type = 'ASSET' AND isDefault = 1 LIMIT 1")
    fun observeDefaultAccount(): Flow<AccountEntity?>

    @Query("SELECT COUNT(*) FROM accounts WHERE type = 'ASSET'")
    suspend fun getAccountCount(): Int

    @Insert
    suspend fun insert(account: AccountEntity): Long

    @Update
    suspend fun update(account: AccountEntity)

    @Delete
    suspend fun delete(account: AccountEntity)
}
