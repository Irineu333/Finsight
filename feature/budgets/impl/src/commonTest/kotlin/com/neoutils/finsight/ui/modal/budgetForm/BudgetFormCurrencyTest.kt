@file:OptIn(ExperimentalCoroutinesApi::class)

package com.neoutils.finsight.ui.modal.budgetForm

import app.cash.turbine.test
import arrow.core.right
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.analytics.Event
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Budget
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.IBudgetRepository
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.repository.IRecurringRepository
import com.neoutils.finsight.domain.usecase.ValidateBudgetTitleUseCase
import com.neoutils.finsight.extension.CurrencyFormatter
import com.neoutils.finsight.ui.component.ModalManager
import com.neoutils.finsight.util.DebounceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
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
 * The budget form asks about currency **only when there is something to ask** (design D13).
 *
 * The two profiles are the whole rule. With one currency among the accounts the form is
 * exactly the form it was before currencies existed — not one control more — and the limit is
 * denominated by the account the user actually transacts from. With two, the choice appears,
 * suggested from the **default account** and not from the base: the base answers *in what
 * currency this user reads totals*, and has nothing to say about a number being typed.
 *
 * That is why this differs from the account form, which shows the currency line always: the
 * account form is the one door a second currency is born through, so it stays open even while
 * there is one. This form creates no currency; it picks among those that exist.
 *
 * The project has no composable test infrastructure, so what is asserted is the state the
 * modal renders from — which is where the decision lives, rather than in a pixel.
 */
class BudgetFormCurrencyTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun account(id: Long, currency: String, isDefault: Boolean = false) = Account(
        id = id,
        name = "Account $id",
        type = AccountType.ASSET,
        currency = currency,
        isDefault = isDefault,
    )

    private fun viewModel(
        accounts: List<Account>,
        budget: Budget? = null,
    ) = BudgetFormViewModel(
        formatter = CurrencyFormatter(),
        budget = budget,
        budgetRepository = NoBudgets,
        categoryRepository = NoCategories,
        recurringRepository = NoRecurrings,
        accountRepository = FixedAccounts(accounts),
        validateBudgetTitle = ValidateBudgetTitleUseCase(NoBudgets),
        modalManager = NoModals,
        debounceManager = DebounceManager(),
        analytics = NoAnalytics,
    )

    @Test
    fun `one currency among the accounts offers no choice and denominates by the default account`() =
        runTest(dispatcher) {
            // Every account in dollars, on a device whose locale resolves reais: this user is
            // single-currency, and pays none of the price of multi-currency.
            val vm = viewModel(
                listOf(
                    account(1, "USD", isDefault = true),
                    account(2, "USD"),
                )
            )

            vm.uiState.test {
                val state = awaitStable()
                assertFalse(state.offersCurrencyChoice, "there is nothing to choose")
                assertEquals("USD", state.currency)
                assertEquals(listOf("USD"), state.offeredCurrencies)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `two currencies offer the choice, suggested from the default account and not the base`() =
        runTest(dispatcher) {
            val vm = viewModel(
                listOf(
                    account(1, "USD", isDefault = true),
                    account(2, "BRL"),
                )
            )

            vm.uiState.test {
                val state = awaitStable()
                assertTrue(state.offersCurrencyChoice)
                // The default account's, even though the last-resort base is BRL and BRL is
                // among the options: where the user transacts is the suggestion.
                assertEquals("USD", state.currency)
                assertEquals(listOf("BRL", "USD"), state.offeredCurrencies)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a chosen currency wins over the suggestion`() = runTest(dispatcher) {
        val vm = viewModel(listOf(account(1, "USD", isDefault = true), account(2, "BRL")))

        vm.uiState.test {
            awaitStable()
            vm.onAction(BudgetFormAction.CurrencySelected("BRL"))
            assertEquals("BRL", awaitStable().currency)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `editing shows the budget's own currency, locked`() = runTest(dispatcher) {
        val budget = Budget(
            id = 1, title = "Food", categories = emptyList(), iconKey = "shopping",
            amount = 200.0, currency = "EUR", createdAt = 0L,
        )
        val vm = viewModel(listOf(account(1, "USD", isDefault = true), account(2, "BRL")), budget)

        vm.uiState.test {
            val state = awaitStable()
            // Not the default account's and not the base: reinterpreting a limit already
            // typed is rewriting what the user meant (design D13, mirroring D12).
            assertEquals("EUR", state.currency)
            assertTrue(state.isCurrencyLocked)
            assertFalse(state.offersCurrencyChoice, "a locked line is state, not a control")
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * The first emission built from the repositories rather than the seed. The seeded initial
     * value knows no account, so it cannot yet answer what currencies exist.
     */
    private suspend fun app.cash.turbine.ReceiveTurbine<BudgetFormUiState>.awaitStable(): BudgetFormUiState {
        var state = awaitItem()
        while (state.offeredCurrencies.isEmpty()) state = awaitItem()
        return state
    }
}

private class FixedAccounts(private val accounts: List<Account>) : IAccountRepository {
    override fun observeAllAccounts(): Flow<List<Account>> = flowOf(accounts)
    override suspend fun getAllAccounts(): List<Account> = accounts
    override suspend fun getAllAccountsIncludingClosed(): List<Account> = accounts
    override fun observeAllAccountsIncludingClosed(): Flow<List<Account>> = flowOf(accounts)
    override suspend fun getAllLedgerAccounts(): List<Account> = throw NotImplementedError()
    override fun observeAllLedgerAccounts(): Flow<List<Account>> = throw NotImplementedError()
    override suspend fun getAccountById(accountId: Long): Account? = accounts.find { it.id == accountId }
    override fun observeAccountById(accountId: Long): Flow<Account?> = throw NotImplementedError()
    override suspend fun getDefaultAccount(): Account? = accounts.find { it.isDefault }
    override fun observeDefaultAccount(): Flow<Account?> = throw NotImplementedError()
    override suspend fun getAccountCount(): Int = accounts.size
    override suspend fun insert(account: Account): Long = throw NotImplementedError()
    override suspend fun update(account: Account) = throw NotImplementedError()
    override suspend fun delete(account: Account) = throw NotImplementedError()
    override suspend fun reopen(accountId: Long) = throw NotImplementedError()
}

private object NoBudgets : IBudgetRepository {
    override fun observeAllBudgets(): Flow<List<Budget>> = flowOf(emptyList())
    override suspend fun getAllBudgets(): List<Budget> = emptyList()
    override suspend fun insert(budget: Budget) = throw NotImplementedError()
    override suspend fun update(budget: Budget) = throw NotImplementedError()
    override suspend fun delete(budget: Budget) = throw NotImplementedError()
    override suspend fun hasBudgetForCategory(categoryId: Long): Boolean = false
    override suspend fun hasBudgetForRecurring(recurringId: Long): Boolean = false
}

private object NoCategories : ICategoryRepository {
    override fun observeAllCategories(): Flow<List<Category>> = flowOf(emptyList())
    override suspend fun getAllCategories(): List<Category> = emptyList()
    override suspend fun getAllCategoriesIncludingClosed(): List<Category> = emptyList()
    override fun observeAllCategoriesIncludingClosed(): Flow<List<Category>> = flowOf(emptyList())
    override fun observeCategoriesByType(type: Category.Type): Flow<List<Category>> = flowOf(emptyList())
    override suspend fun getCategoryById(id: Long): Category? = null
    override suspend fun getCategoryByDimensionId(dimensionId: Long): Category? = null
    override fun observeCategoryById(id: Long): Flow<Category?> = flowOf(null)
    override suspend fun archive(id: Long) = throw NotImplementedError()
    override suspend fun unarchive(id: Long) = throw NotImplementedError()
    override suspend fun existsByName(name: String, ignoreId: Long): Boolean = false
    override suspend fun insert(category: Category) = throw NotImplementedError()
    override suspend fun insertAll(categories: List<Category>) = throw NotImplementedError()
    override suspend fun update(category: Category) = throw NotImplementedError()
    override suspend fun delete(category: Category) = throw NotImplementedError()
}

private object NoRecurrings : IRecurringRepository {
    override suspend fun hasRecurringForCategory(categoryId: Long) = false
    override suspend fun hasTransactionForRecurring(recurringId: Long) = false
    override suspend fun hasRecurringForAccount(accountId: Long) = false
    override suspend fun hasRecurringForCreditCard(creditCardId: Long) = false
    override fun observeAllRecurring(): Flow<List<Recurring>> = flowOf(emptyList())
    override fun observeRecurringById(id: Long): Flow<Recurring?> = flowOf(null)
    override suspend fun getRecurringById(id: Long): Recurring? = null
    override suspend fun insert(recurring: Recurring) = throw NotImplementedError()
    override suspend fun update(recurring: Recurring) = throw NotImplementedError()
    override suspend fun delete(recurring: Recurring) = throw NotImplementedError()
}

/** The form never opens a modal in these cases; a real manager is inert here. */
private val NoModals = ModalManager()

private object NoAnalytics : Analytics {
    override fun logEvent(event: Event) = Unit
    override fun logScreenView(screenName: String) = Unit
    override fun setUserId(id: String?) = Unit
}
