@file:OptIn(ExperimentalCoroutinesApi::class)

package com.neoutils.finsight.ui.modal.accountForm

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.usecase.CreateAccountUseCase
import com.neoutils.finsight.domain.usecase.EnsureYieldCategoryUseCase
import com.neoutils.finsight.domain.usecase.SetDefaultAccountUseCase
import com.neoutils.finsight.domain.usecase.SuggestAccountIconUseCase
import com.neoutils.finsight.domain.usecase.UpdateAccountUseCase
import com.neoutils.finsight.domain.usecase.ValidateAccountNameUseCase
import com.neoutils.finsight.ui.component.ModalManager
import com.neoutils.finsight.ui.screen.accounts.FakeCategoryRepository
import com.neoutils.finsight.util.AppIcon
import com.neoutils.finsight.util.DebounceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

/**
 * The icon a new account form opens on.
 *
 * The form only *consumes* the suggestion — the criterion and the order of preference
 * belong to the use case. What is at stake here is when it applies: never while
 * editing, and never over a choice the user already made.
 */
class AccountFormIconSuggestionTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setup() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        account: Account?,
        suggestAccountIcon: CountingSuggestion,
    ): AccountFormViewModel {
        val repository = StubAccounts(listOfNotNull(account))
        return AccountFormViewModel(
            account = account,
            validateAccountName = ValidateAccountNameUseCase(repository),
            suggestAccountIcon = suggestAccountIcon,
            baseCurrencyRepository = StubBaseCurrency("BRL"),
            currencyRepository = StubCurrencies(),
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
            ensureYieldCategory = EnsureYieldCategoryUseCase(FakeCategoryRepository()),
            modalManager = ModalManager(),
            debounceManager = DebounceManager(),
            analytics = StubAnalytics(),
            crashlytics = StubCrashlytics(),
        )
    }

    @Test
    fun `creating opens on the suggested icon`() = runTest(dispatcher) {
        val suggestion = CountingSuggestion(AppIcon.BANK)
        val viewModel = viewModel(account = null, suggestAccountIcon = suggestion)

        backgroundScope.launch { viewModel.uiState.collect { } }
        advanceUntilIdle()

        assertEquals(AppIcon.BANK, viewModel.uiState.value.selectedIcon)
        assertEquals(1, suggestion.calls)
    }

    @Test
    fun `a choice made before the suggestion arrives wins`() = runTest(dispatcher) {
        val suggestion = CountingSuggestion(AppIcon.BANK)
        val viewModel = viewModel(account = null, suggestAccountIcon = suggestion)

        // Nothing has been advanced yet, so the suggestion is still in flight — the
        // user reaching the picker first is exactly the race that must not be lost.
        viewModel.onAction(AccountFormAction.IconSelected(AppIcon.SAVINGS))

        backgroundScope.launch { viewModel.uiState.collect { } }
        advanceUntilIdle()

        assertEquals(AppIcon.SAVINGS, viewModel.uiState.value.selectedIcon)
    }

    @Test
    fun `editing keeps the account's own icon and asks for no suggestion`() = runTest(dispatcher) {
        val suggestion = CountingSuggestion(AppIcon.BANK)
        val account = Account(id = 1, name = "Chase", currency = "BRL", iconKey = AppIcon.CARD.key)
        val viewModel = viewModel(account = account, suggestAccountIcon = suggestion)

        backgroundScope.launch { viewModel.uiState.collect { } }
        advanceUntilIdle()

        assertEquals(AppIcon.CARD, viewModel.uiState.value.selectedIcon)
        assertEquals(0, suggestion.calls)
    }
}

private class CountingSuggestion(private val icon: AppIcon) : SuggestAccountIconUseCase {
    var calls = 0
        private set

    override suspend fun invoke(): AppIcon {
        calls++
        return icon
    }
}
