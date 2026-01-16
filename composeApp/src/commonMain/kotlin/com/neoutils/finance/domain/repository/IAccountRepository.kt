package com.neoutils.finance.domain.repository

import com.neoutils.finance.domain.model.Account
import kotlinx.coroutines.flow.Flow

interface IAccountRepository {
    fun observeAllAccounts(): Flow<List<Account>>
    suspend fun getAllAccounts(): List<Account>
    suspend fun getAccountById(accountId: Long): Account?
    fun observeAccountById(accountId: Long): Flow<Account?>
    suspend fun getDefaultAccount(): Account?
    fun observeDefaultAccount(): Flow<Account?>
    suspend fun getAccountCount(): Int
    suspend fun insert(account: Account): Long
    suspend fun update(account: Account)
    suspend fun delete(account: Account)
}
