package com.neoutils.finsight.database.repository

import com.neoutils.finsight.database.dao.AccountDao
import com.neoutils.finsight.database.entity.AccountEntity
import com.neoutils.finsight.database.mapper.AccountMapper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AccountRepositoryTest {

    private class RecordingAccountDao : AccountDao {
        val reopened = mutableListOf<Long>()
        override suspend fun reopen(id: Long) { reopened += id }
        override suspend fun close(id: Long) = throw NotImplementedError()
        override suspend fun entryCount(accountId: Long): Int = throw NotImplementedError()
        override fun observeAllAccounts(): Flow<List<AccountEntity>> = flowOf(emptyList())
        override suspend fun getAllAccounts(): List<AccountEntity> = emptyList()
        override suspend fun getAllAccountsIncludingClosed(): List<AccountEntity> = emptyList()
        override fun observeAllAccountsIncludingClosed(): Flow<List<AccountEntity>> = flowOf(emptyList())
        override suspend fun getAllLedgerAccounts(): List<AccountEntity> = emptyList()
        override fun observeAllLedgerAccounts(): Flow<List<AccountEntity>> = flowOf(emptyList())
        override suspend fun getAccountById(id: Long): AccountEntity? = throw NotImplementedError()
        override suspend fun getByTypeAndName(type: AccountEntity.Type, name: String): AccountEntity? = throw NotImplementedError()
        override fun observeAccountById(id: Long): Flow<AccountEntity?> = throw NotImplementedError()
        override suspend fun getDefaultAccount(): AccountEntity? = throw NotImplementedError()
        override fun observeDefaultAccount(): Flow<AccountEntity?> = throw NotImplementedError()
        override suspend fun getAccountCount(): Int = throw NotImplementedError()
        override suspend fun insert(account: AccountEntity): Long = throw NotImplementedError()
        override suspend fun update(account: AccountEntity) = throw NotImplementedError()
        override suspend fun delete(account: AccountEntity) = throw NotImplementedError()
    }

    @Test
    fun `reopen forwards the account id to the dao`() = runTest {
        val dao = RecordingAccountDao()
        val repository = AccountRepository(dao = dao, mapper = AccountMapper())

        repository.reopen(accountId = 42L)

        assertEquals(listOf(42L), dao.reopened)
    }
}
