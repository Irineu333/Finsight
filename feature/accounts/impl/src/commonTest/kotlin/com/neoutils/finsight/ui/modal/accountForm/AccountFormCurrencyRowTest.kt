@file:OptIn(ExperimentalCoroutinesApi::class)

package com.neoutils.finsight.ui.modal.accountForm

import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.analytics.Event
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.CurrencyInfo
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.IBaseCurrencyRepository
import com.neoutils.finsight.domain.repository.ICurrencyRepository
import com.neoutils.finsight.domain.usecase.CreateAccountUseCaseImpl
import com.neoutils.finsight.domain.usecase.EnsureYieldCategoryUseCase
import com.neoutils.finsight.ui.screen.accounts.FakeCategoryRepository
import com.neoutils.finsight.domain.usecase.SetDefaultAccountUseCaseImpl
import com.neoutils.finsight.domain.usecase.SuggestAccountIconUseCaseImpl
import com.neoutils.finsight.domain.usecase.UpdateAccountUseCaseImpl
import com.neoutils.finsight.domain.usecase.ValidateAccountNameUseCase
import com.neoutils.finsight.ui.component.ModalManager
import com.neoutils.finsight.util.DebounceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
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
            suggestAccountIcon = SuggestAccountIconUseCaseImpl(repository),
            baseCurrencyRepository = StubBaseCurrency(base),
            currencyRepository = StubCurrencies(),
            createAccountUseCase = CreateAccountUseCaseImpl(
                repository = repository,
                validateAccountName = ValidateAccountNameUseCase(repository),
                setDefaultAccount = SetDefaultAccountUseCaseImpl(repository),
            ),
            updateAccountUseCase = UpdateAccountUseCaseImpl(
                repository = repository,
                validateAccountName = ValidateAccountNameUseCase(repository),
                setDefaultAccount = SetDefaultAccountUseCaseImpl(repository),
            ),
            // The form declares whether an account yields; this test is about the
            // currency row, and never submits, so the category is never reached.
            ensureYieldCategory = EnsureYieldCategoryUseCase(FakeCategoryRepository()),
            modalManager = ModalManager(),
            debounceManager = DebounceManager(),
            analytics = StubAnalytics(),
            crashlytics = StubCrashlytics(),
        )
    }

    @Test
    fun `creating offers the choice pre-selected with the base currency`() = runTest {
        // Awaited rather than read from the initial value: the offered set is stored
        // data now, so it arrives one emission after the form opens.
        val uiState = viewModel(account = null, base = "USD")
            .uiState
            .first { it.selectableCurrencies.isNotEmpty() }

        assertEquals("USD", uiState.currency)
        assertTrue(uiState.canChangeCurrency)
    }

    @Test
    fun `editing shows the account's own currency and locks it`() = runTest {
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

internal class StubBaseCurrency(base: String) : IBaseCurrencyRepository {
    private val flow = MutableStateFlow(base)
    override fun observe(): StateFlow<String> = flow
    override suspend fun set(code: String) { flow.value = code }
}

internal class StubAnalytics : Analytics {
    override fun logScreenView(screenName: String) = Unit
    override fun logEvent(event: Event) = Unit
    override fun setUserId(id: String?) = Unit
}

internal class StubCrashlytics : Crashlytics {
    override fun setUserId(id: String?) = Unit
    override fun recordException(e: Throwable) = Unit
}

/**
 * The account facade as the form reads it: `getAllAccounts` answers with the open
 * ones only, exactly as the query behind it does — the archived ones reach nothing
 * but the listing that asks for them by name.
 */
internal class StubAccounts(
    private val accounts: List<Account> = emptyList(),
) : IAccountRepository {
    override suspend fun hasYieldingAccount(): Boolean = false
    override suspend fun getAllAccountsIncludingClosed(): List<Account> = accounts
    override suspend fun getAllAccounts(): List<Account> = accounts.filterNot { it.isArchived }
    override suspend fun getAccountById(accountId: Long): Account? = null
    override suspend fun update(account: Account) = Unit
    override suspend fun delete(account: Account) = Unit
    override suspend fun reopen(accountId: Long) = Unit
    override fun observeAllAccounts(): Flow<List<Account>> = flowOf(accounts.filterNot { it.isArchived })
    override fun observeAllAccountsIncludingClosed(): Flow<List<Account>> = flowOf(accounts)
    override suspend fun getAllLedgerAccounts(): List<Account> = accounts
    override fun observeAllLedgerAccounts(): Flow<List<Account>> = flowOf(accounts)
    override fun observeAccountById(accountId: Long): Flow<Account?> = flowOf(null)
    override suspend fun getDefaultAccount(): Account? = null
    override fun observeDefaultAccount(): Flow<Account?> = flowOf(null)
    override suspend fun getAccountCount(): Int = 0
    override suspend fun insert(account: Account): Long = 1L
}

/**
 * The registry, as this form reads it: a list of offered rows. Which rows they are does
 * not matter here — what the form does with the currency is the subject, and the set is
 * stored data with an owner of its own.
 */
internal class StubCurrencies : ICurrencyRepository {
    private val rows = listOf(
        CurrencyInfo("BRL", "R$", "Real brasileiro"),
        CurrencyInfo("USD", "US$", "Dólar americano"),
    )

    override fun observeOffered(): Flow<List<CurrencyInfo>> = flowOf(rows)
    override fun observeAll(): Flow<List<CurrencyInfo>> = flowOf(rows)
    override suspend fun getOffered(): List<CurrencyInfo> = rows
    override suspend fun getAll(): List<CurrencyInfo> = rows
    override suspend fun get(code: String): CurrencyInfo? = rows.firstOrNull { it.code == code }
    override suspend fun exists(code: String): Boolean = get(code) != null
    override suspend fun save(code: String, symbol: String, name: String?) = Unit
    override suspend fun archive(code: String) = Unit
    override suspend fun unarchive(code: String) = Unit
    override suspend fun delete(code: String) = Unit
}
