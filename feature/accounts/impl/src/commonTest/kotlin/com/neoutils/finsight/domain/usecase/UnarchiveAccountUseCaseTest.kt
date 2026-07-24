package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.repository.IAccountRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UnarchiveAccountUseCaseTest {

    private class RecordingAccountRepository : IAccountRepository {
        val reopened = mutableListOf<Long>()
        override suspend fun reopen(accountId: Long) { reopened += accountId }
        override fun observeAllAccounts(): Flow<List<Account>> = flowOf(emptyList())
        override suspend fun getAllAccounts(): List<Account> = emptyList()
        override suspend fun getAllAccountsIncludingClosed(): List<Account> = emptyList()
        override fun observeAllAccountsIncludingClosed(): Flow<List<Account>> = flowOf(emptyList())
        override suspend fun getAllLedgerAccounts(): List<Account> = emptyList()
        override fun observeAllLedgerAccounts(): Flow<List<Account>> = flowOf(emptyList())
        override suspend fun getAccountById(accountId: Long): Account? = throw NotImplementedError()
        override fun observeAccountById(accountId: Long): Flow<Account?> = throw NotImplementedError()
        override suspend fun getDefaultAccount(): Account? = throw NotImplementedError()
        override fun observeDefaultAccount(): Flow<Account?> = throw NotImplementedError()
        override suspend fun getAccountCount(): Int = throw NotImplementedError()
        override suspend fun insert(account: Account): Long = throw NotImplementedError()
        override suspend fun update(account: Account) = throw NotImplementedError()
        override suspend fun delete(account: Account) = throw NotImplementedError()
    }

    @Test
    fun `unarchive reopens the account by its id and returns Right`() = runTest {
        val repository = RecordingAccountRepository()
        val account = Account(id = 7, name = "Wallet", type = AccountType.ASSET, isArchived = true)

        val result = UnarchiveAccountUseCase(repository)(account)

        assertTrue(result.isRight())
        assertEquals(listOf(7L), repository.reopened)
    }
}
