@file:OptIn(ExperimentalCoroutinesApi::class)

package com.neoutils.finsight.ui.modal.accountForm

import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.analytics.Event
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.IBaseCurrencyRepository
import com.neoutils.finsight.domain.usecase.CreateAccountUseCase
import com.neoutils.finsight.domain.usecase.SetDefaultAccountUseCase
import com.neoutils.finsight.domain.usecase.UpdateAccountUseCase
import com.neoutils.finsight.domain.usecase.ValidateAccountNameUseCase
import com.neoutils.finsight.ui.component.ModalManager
import com.neoutils.finsight.util.DebounceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The currency row of the account form — design D23 and D12.
 *
 * Two properties, and both are decided by the **mode of the form** rather than by the
 * state of the account: it is a picker while creating, pre-selected with the base
 * currency, and a locked state row while editing, always. The row itself is never
 * absent, because the account form is the only door a second currency is ever born
 * through — hide it while there is one currency and there will never be a second.
 */
class AccountFormCurrencyRowTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setup() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(account: Account?, base: String = "BRL"): AccountFormViewModel {
        val repository = StubAccounts()
        return AccountFormViewModel(
            account = account,
            validateAccountName = ValidateAccountNameUseCase(repository),
            baseCurrencyRepository = StubBaseCurrency(base),
            createAccountUseCase = CreateAccountUseCase(
                repository = repository,
                validateAccountName = ValidateAccountNameUseCase(repository),
                setDefaultAccount = SetDefaultAccountUseCase(repository),
            ),
            updateAccountUseCase = UpdateAccountUseCase(
                repository = repository,
                validateAccountName = ValidateAccountNameUseCase(repository),
                setDefaultAccount = SetDefaultAccountUseCase(repository),
            ),
            modalManager = ModalManager(),
            debounceManager = DebounceManager(),
            analytics = StubAnalytics(),
            crashlytics = StubCrashlytics(),
        )
    }

    @Test
    fun `creating offers the choice, pre-selected with the base currency`() = runTest {
        val uiState = viewModel(account = null, base = "USD").uiState.value

        assertEquals("USD", uiState.currency)
        assertTrue(uiState.canChangeCurrency)
        assertTrue(uiState.selectableCurrencies.isNotEmpty(), "there is always something to pick")
    }

    @Test
    fun `editing shows the account's own currency, locked`() = runTest {
        val account = Account(id = 1, name = "Chase", currency = "USD")

        val uiState = viewModel(account = account, base = "BRL").uiState.value

        // The account's own currency and not the base: an account states what it is
        // denominated in, whatever the user reads totals in (design D29).
        assertEquals("USD", uiState.currency)
        assertFalse(uiState.canChangeCurrency)
    }

    @Test
    fun `a currency chosen while editing changes nothing`() = runTest {
        val viewModel = viewModel(account = Account(id = 1, name = "Chase", currency = "USD"))
        backgroundScope.launch { viewModel.uiState.collect { } }
        advanceUntilIdle()

        viewModel.onAction(AccountFormAction.CurrencySelected("EUR"))
        advanceUntilIdle()

        assertEquals("USD", viewModel.uiState.value.currency)
    }

    @Test
    fun `a currency chosen while creating is the one the account is born in`() = runTest {
        val viewModel = viewModel(account = null)
        backgroundScope.launch { viewModel.uiState.collect { } }
        advanceUntilIdle()

        viewModel.onAction(AccountFormAction.CurrencySelected("EUR"))
        advanceUntilIdle()

        assertEquals("EUR", viewModel.uiState.value.currency)
    }
}

private class StubBaseCurrency(base: String) : IBaseCurrencyRepository {
    private val flow = MutableStateFlow(base)
    override fun observe(): StateFlow<String> = flow
    override suspend fun set(currency: String) { flow.value = currency }
}

private class StubAnalytics : Analytics {
    override fun logScreenView(screenName: String) = Unit
    override fun logEvent(event: Event) = Unit
    override fun setUserId(id: String?) = Unit
}

private class StubCrashlytics : Crashlytics {
    override fun setUserId(id: String?) = Unit
    override fun recordException(e: Throwable) = Unit
}

private class StubAccounts : IAccountRepository {
    override suspend fun getAllAccountsIncludingClosed(): List<Account> = emptyList()
    override suspend fun getAllAccounts(): List<Account> = emptyList()
    override suspend fun getAccountById(accountId: Long): Account? = null
    override suspend fun update(account: Account) = Unit
    override suspend fun delete(account: Account) = Unit
    override suspend fun reopen(accountId: Long) = Unit
    override fun observeAllAccounts(): Flow<List<Account>> = flowOf(emptyList())
    override fun observeAllAccountsIncludingClosed(): Flow<List<Account>> = flowOf(emptyList())
    override suspend fun getAllLedgerAccounts(): List<Account> = emptyList()
    override fun observeAllLedgerAccounts(): Flow<List<Account>> = flowOf(emptyList())
    override fun observeAccountById(accountId: Long): Flow<Account?> = flowOf(null)
    override suspend fun getDefaultAccount(): Account? = null
    override fun observeDefaultAccount(): Flow<Account?> = flowOf(null)
    override suspend fun getAccountCount(): Int = 0
    override suspend fun insert(account: Account): Long = 1L
}
