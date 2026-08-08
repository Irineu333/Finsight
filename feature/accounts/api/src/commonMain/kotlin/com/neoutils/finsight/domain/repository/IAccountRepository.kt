package com.neoutils.finsight.domain.repository

import com.neoutils.finsight.domain.model.Account
import kotlinx.coroutines.flow.Flow

interface IAccountRepository {
    fun observeAllAccounts(): Flow<List<Account>>
    suspend fun getAllAccounts(): List<Account>

    /**
     * Every account, closed ones included. Name uniqueness needs it: a closed
     * account keeps its name, and a homonym created after it would be
     * indistinguishable from it wherever history is rendered.
     */
    suspend fun getAllAccountsIncludingClosed(): List<Account>

    fun observeAllAccountsIncludingClosed(): Flow<List<Account>>

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

    /**
     * Whether any open account declares that it yields.
     *
     * It is the fourth guard on retiring the yield category — which is protected
     * while someone uses it, and removable again once nobody does. No read of a
     * total consults it: the yield line comes from the dimension, never from the
     * declaration.
     */
    suspend fun hasYieldingAccount(): Boolean
    suspend fun insert(account: Account): Long
    suspend fun update(account: Account)
    suspend fun delete(account: Account)

    /**
     * The inverse of closing an account: flips the `isArchived` flag back off and
     * touches nothing else — no entry. Safe by invariant, since archiving already
     * required a zero balance.
     */
    suspend fun reopen(accountId: Long)
}
