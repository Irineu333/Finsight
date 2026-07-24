@file:OptIn(ExperimentalCoroutinesApi::class)

package com.neoutils.finsight.ui.modal.viewAccount

import app.cash.turbine.turbineScope
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.usecase.UnarchiveAccountUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ViewAccountViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setup() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private class FakeCrashlytics : Crashlytics {
        override fun setUserId(id: String?) = Unit
        override fun recordException(e: Throwable) = Unit
    }

    private class FakeAccountRepository : IAccountRepository {
        private val byId = MutableSharedFlow<Account?>(replay = 1)
        private var current: Account? = null
        val reopened = mutableListOf<Long>()
        fun emit(account: Account?) { current = account; byId.tryEmit(account) }
        override fun observeAccountById(accountId: Long): Flow<Account?> = byId
        override suspend fun getAccountById(accountId: Long): Account? = current
        override suspend fun reopen(accountId: Long) { reopened += accountId }
        override fun observeAllAccounts(): Flow<List<Account>> = flowOf(emptyList())
        override suspend fun getAllAccounts(): List<Account> = throw NotImplementedError()
        override suspend fun getAllAccountsIncludingClosed(): List<Account> = throw NotImplementedError()
        override fun observeAllAccountsIncludingClosed(): Flow<List<Account>> = throw NotImplementedError()
        override suspend fun getAllLedgerAccounts(): List<Account> = throw NotImplementedError()
        override fun observeAllLedgerAccounts(): Flow<List<Account>> = throw NotImplementedError()
        override suspend fun getDefaultAccount(): Account? = throw NotImplementedError()
        override fun observeDefaultAccount(): Flow<Account?> = throw NotImplementedError()
        override suspend fun getAccountCount(): Int = throw NotImplementedError()
        override suspend fun insert(account: Account): Long = throw NotImplementedError()
        override suspend fun update(account: Account) = throw NotImplementedError()
        override suspend fun delete(account: Account) = throw NotImplementedError()
    }

    private fun account(id: Long = 1L, isArchived: Boolean = true) = Account(
        id = id,
        name = "Wallet",
        type = AccountType.ASSET,
        isArchived = isArchived,
    )

    private fun viewModel(repository: FakeAccountRepository) = ViewAccountViewModel(
        accountId = 1L,
        accountRepository = repository,
        unarchiveAccount = UnarchiveAccountUseCase(repository),
        crashlytics = FakeCrashlytics(),
    )

    @Test
    fun `the unarchive action reopens the shown account and dismisses`() = runTest(dispatcher) {
        val repository = FakeAccountRepository()
        val vm = viewModel(repository)

        turbineScope {
            val state = vm.uiState.testIn(backgroundScope)
            val events = vm.events.testIn(backgroundScope)

            assertEquals(ViewAccountUiState.Loading, state.awaitItem())
            repository.emit(account(id = 1L, isArchived = true))
            assertIs<ViewAccountUiState.Content>(state.awaitItem())

            vm.onAction(ViewAccountAction.Unarchive)
            runCurrent()

            assertEquals(listOf(1L), repository.reopened)
            assertIs<ViewAccountEvent.Dismiss>(events.awaitItem())

            state.cancel()
            events.cancel()
        }
    }
}
