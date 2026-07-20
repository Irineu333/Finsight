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
    // reconciliation and must not leak into the accounts facade. Closed accounts
    // keep their history but leave the active listings and selectors (design D21).
    @Query("SELECT * FROM accounts WHERE type = 'ASSET' AND isArchived = 0 ORDER BY createdAt ASC")
    fun observeAllAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE type = 'ASSET' AND isArchived = 0 ORDER BY createdAt ASC")
    suspend fun getAllAccounts(): List<AccountEntity>

    /**
     * The account facade, closed ones included. Name uniqueness needs it: a closed
     * account keeps its name, and a homonym created after it would be
     * indistinguishable from it wherever history is rendered.
     */
    @Query("SELECT * FROM accounts WHERE type = 'ASSET' ORDER BY createdAt ASC")
    suspend fun getAllAccountsIncludingClosed(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE type = 'ASSET' ORDER BY createdAt ASC")
    fun observeAllAccountsIncludingClosed(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getAccountById(id: Long): AccountEntity?

    // Ledger account lookups (chart of accounts includes non-ASSET rows).
    @Query("SELECT * FROM accounts WHERE type = :type AND name = :name LIMIT 1")
    suspend fun getByTypeAndName(type: AccountEntity.Type, name: String): AccountEntity?

    @Query("SELECT * FROM accounts WHERE id = :id")
    fun observeAccountById(id: Long): Flow<AccountEntity?>

    @Query("SELECT * FROM accounts WHERE type = 'ASSET' AND isArchived = 0 AND isDefault = 1 LIMIT 1")
    suspend fun getDefaultAccount(): AccountEntity?

    @Query("SELECT * FROM accounts WHERE type = 'ASSET' AND isArchived = 0 AND isDefault = 1 LIMIT 1")
    fun observeDefaultAccount(): Flow<AccountEntity?>

    /**
     * The whole chart of accounts — every type, closed included.
     *
     * Hydrating a ledger entry needs this, not the ASSET facade above: a card
     * purchase has no asset leg at all, and an entry on an account missing from the
     * map is silently dropped.
     */
    @Query("SELECT * FROM accounts ORDER BY id ASC")
    suspend fun getAllLedgerAccounts(): List<AccountEntity>

    @Query("SELECT * FROM accounts ORDER BY id ASC")
    fun observeAllLedgerAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT COUNT(*) FROM accounts WHERE type = 'ASSET' AND isArchived = 0")
    suspend fun getAccountCount(): Int

    /**
     * Closing is the only way an account with history leaves the app: the rows
     * that reference it stay valid, and reopening is a single flag away.
     */
    @Query("UPDATE accounts SET isArchived = 1 WHERE id = :id")
    suspend fun close(id: Long)

    @Query("SELECT COUNT(*) FROM entries WHERE accountId = :accountId")
    suspend fun entryCount(accountId: Long): Int

    @Insert
    suspend fun insert(account: AccountEntity): Long

    @Update
    suspend fun update(account: AccountEntity)

    @Delete
    suspend fun delete(account: AccountEntity)
}
