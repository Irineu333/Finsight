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

    /**
     * The currencies the user actually holds money in.
     *
     * `ASSET` and `LIABILITY` together, because an account and the card's own row are
     * lines of this same table and a user whose foreign spending is all on a card holds
     * that currency just as much. The nominals, reconciliation and conversion are out by
     * the type filter alone; the reconstructed [SystemAccount.CLOSED_ACCOUNT] and
     * [SystemAccount.CLOSED_CARD] rows are excluded by name, since they are stand-ins
     * for facades the user deleted and not currencies they chose.
     *
     * Archived rows are out too: a currency nobody holds any more must not keep
     * offering a choice to a user who is single-currency again.
     */
    @Query(
        "SELECT DISTINCT currency FROM accounts " +
            "WHERE type IN ('ASSET', 'LIABILITY') AND isArchived = 0 " +
            "AND name NOT IN (:systemNames)"
    )
    suspend fun currenciesInUse(systemNames: List<String>): List<String>

    /**
     * A system row of the chart, by the triple that identifies one:
     * `(type, name, currency)`.
     *
     * The currency is part of the key, not a detail of the row it returns. There is
     * one nominal per nature **per currency in use** — an expense in USD lands on
     * `EXPENSES/USD` — because that is what keeps `Account.currency` meaning the same
     * thing on every line of the chart, user rows and system rows alike (design D4).
     * With a single nominal, an expense in USD would land on the `EXPENSES` row whose
     * currency says `BRL`, and the column would start meaning two things.
     */
    @Query("SELECT * FROM accounts WHERE type = :type AND name = :name AND currency = :currency LIMIT 1")
    suspend fun getByTypeAndName(
        type: AccountEntity.Type,
        name: String,
        currency: String,
    ): AccountEntity?

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

    /**
     * The inverse of [close]: it flips the archival flag back on and touches
     * nothing else — no entry, no invoice. Reopening a closed account is safe by
     * invariant: closing already required a zero balance, so there is nothing to
     * reconcile.
     */
    @Query("UPDATE accounts SET isArchived = 0 WHERE id = :id")
    suspend fun reopen(id: Long)

    @Query("SELECT COUNT(*) FROM entries WHERE accountId = :accountId")
    suspend fun entryCount(accountId: Long): Int

    /**
     * How many accounts are denominated in a currency.
     *
     * It is a question about **accounts**, not about which currencies the app offers:
     * the ledger goes on knowing nothing of that set, and names no table of it. What
     * reads this is a refusal above — a currency an account is denominated in cannot be
     * deleted, because deleting it would leave a figure nobody can name.
     */
    @Query("SELECT COUNT(*) FROM accounts WHERE currency = :currency")
    suspend fun countByCurrency(currency: String): Int

    @Insert
    suspend fun insert(account: AccountEntity): Long

    @Update
    suspend fun update(account: AccountEntity)

    @Delete
    suspend fun delete(account: AccountEntity)
}
