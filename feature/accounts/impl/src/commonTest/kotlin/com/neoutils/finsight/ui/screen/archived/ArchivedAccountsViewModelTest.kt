@file:OptIn(ExperimentalCoroutinesApi::class)

package com.neoutils.finsight.ui.screen.archived

import app.cash.turbine.test
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.repository.IAccountRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ArchivedAccountsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setup() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private class FakeAccountRepository : IAccountRepository {
        private val all = MutableSharedFlow<List<Account>>(replay = 1)
        fun emit(accounts: List<Account>) { all.tryEmit(accounts) }
        override fun observeAllAccountsIncludingClosed(): Flow<List<Account>> = all
        override fun observeAllAccounts(): Flow<List<Account>> = flowOf(emptyList())
        override suspend fun getAllAccounts(): List<Account> = throw NotImplementedError()
        override suspend fun getAllAccountsIncludingClosed(): List<Account> = throw NotImplementedError()
        override suspend fun getAllLedgerAccounts(): List<Account> = throw NotImplementedError()
        override fun observeAllLedgerAccounts(): Flow<List<Account>> = throw NotImplementedError()
        override suspend fun getAccountById(accountId: Long): Account? = throw NotImplementedError()
        override fun observeAccountById(accountId: Long): Flow<Account?> = throw NotImplementedError()
        override suspend fun getDefaultAccount(): Account? = throw NotImplementedError()
        override fun observeDefaultAccount(): Flow<Account?> = throw NotImplementedError()
        override suspend fun getAccountCount(): Int = throw NotImplementedError()
        override suspend fun insert(account: Account): Long = throw NotImplementedError()
        override suspend fun update(account: Account) = throw NotImplementedError()
        override suspend fun delete(account: Account) = throw NotImplementedError()
        override suspend fun reopen(accountId: Long) = throw NotImplementedError()
    }

    private fun account(id: Long, isArchived: Boolean) = Account(
        id = id,
        name = "Account $id",
        type = AccountType.ASSET,
        isArchived = isArchived,
    )

    @Test
    fun `lists only archived accounts`() = runTest(dispatcher) {
        val repository = FakeAccountRepository()
        val vm = ArchivedAccountsViewModel(repository)

        vm.uiState.test {
            assertEquals(ArchivedAccountsUiState.Loading, awaitItem())
            repository.emit(
                listOf(
                    account(id = 1L, isArchived = false),
                    account(id = 2L, isArchived = true),
                    account(id = 3L, isArchived = true),
                )
            )
            val content = assertIs<ArchivedAccountsUiState.Content>(awaitItem())
            assertEquals(listOf(2L, 3L), content.accounts.map { it.accountId })
        }
    }

    @Test
    fun `empty when there are no archived accounts`() = runTest(dispatcher) {
        val repository = FakeAccountRepository()
        val vm = ArchivedAccountsViewModel(repository)

        vm.uiState.test {
            assertEquals(ArchivedAccountsUiState.Loading, awaitItem())
            repository.emit(listOf(account(id = 1L, isArchived = false)))
            assertEquals(ArchivedAccountsUiState.Empty, awaitItem())
        }
    }
}
