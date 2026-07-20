package com.neoutils.finsight.domain.repository

import com.neoutils.finsight.domain.model.Account
import kotlinx.coroutines.flow.Flow

interface IAccountRepository {
    fun observeAllAccounts(): Flow<List<Account>>
    suspend fun getAllAccounts(): List<Account>

    /**
     * The whole chart of accounts, as opposed to the user-facing account facade
     * above. Reading the ledger needs it: a category or card leg lives on an
     * `EXPENSE`/`LIABILITY` account that the facade deliberately hides.
     */
    suspend fun getAllLedgerAccounts(): List<Account>

    fun observeAllLedgerAccounts(): Flow<List<Account>>
    suspend fun getAccountById(accountId: Long): Account?
    fun observeAccountById(accountId: Long): Flow<Account?>
    suspend fun getDefaultAccount(): Account?
    fun observeDefaultAccount(): Flow<Account?>
    suspend fun getAccountCount(): Int
    suspend fun insert(account: Account): Long
    suspend fun update(account: Account)
    suspend fun delete(account: Account)
}
