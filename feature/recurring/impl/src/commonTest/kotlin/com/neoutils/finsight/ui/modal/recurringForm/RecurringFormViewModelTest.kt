@file:OptIn(ExperimentalCoroutinesApi::class)

package com.neoutils.finsight.ui.modal.recurringForm

import com.neoutils.finsight.FakeCrashlytics
import com.neoutils.finsight.FakeRecurringRepository
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.analytics.Event
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.domain.model.form.RecurringForm
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.usecase.SaveRecurringUseCase
import com.neoutils.finsight.recurring
import com.neoutils.finsight.ui.component.ModalManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
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
 * Editing is not unarchiving. The save use case defaults `isArchived` to false, so a
 * form that forgot to pass the current value would quietly put an archived recurring
 * back in circulation — the one call site has to say it explicitly.
 */
class RecurringFormViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private val account = Account(
        id = 1L,
        name = "Checking",
        type = com.neoutils.finsight.domain.model.AccountType.ASSET,
        isDefault = true,
    )

    private val categoryRepository = object : ICategoryRepository {
        override fun observeAllCategories(): Flow<List<Category>> = flowOf(emptyList())
        override suspend fun getAllCategories(): List<Category> = emptyList()
        override suspend fun getAllCategoriesIncludingClosed(): List<Category> = emptyList()
        override fun observeAllCategoriesIncludingClosed(): Flow<List<Category>> = flowOf(emptyList())
        override fun observeCategoriesByType(type: Category.Type): Flow<List<Category>> = flowOf(emptyList())
        override suspend fun getCategoryById(id: Long): Category? = null
        override suspend fun getCategoryBySystemKey(systemKey: String): Category? = null
        override suspend fun getCategoryByDimensionId(dimensionId: Long): Category? = null
        override fun observeCategoryById(id: Long): Flow<Category?> = flowOf(null)
        override suspend fun archive(id: Long) = Unit
        override suspend fun unarchive(id: Long) = Unit
        override suspend fun existsByName(name: String, ignoreId: Long): Boolean = false
        override suspend fun insert(category: Category) = Unit
        override suspend fun insertAll(categories: List<Category>) = Unit
        override suspend fun update(category: Category) = Unit
        override suspend fun delete(category: Category) = Unit
    }

    private val accountRepository = object : IAccountRepository {
        override fun observeAllAccounts(): Flow<List<Account>> = flowOf(listOf(account))
        override suspend fun getAllAccounts(): List<Account> = listOf(account)
        override suspend fun getAllAccountsIncludingClosed(): List<Account> = listOf(account)
        override fun observeAllAccountsIncludingClosed(): Flow<List<Account>> = flowOf(listOf(account))
        override suspend fun getAllLedgerAccounts(): List<Account> = emptyList()
        override fun observeAllLedgerAccounts(): Flow<List<Account>> = flowOf(emptyList())
        override suspend fun getAccountById(accountId: Long): Account? = account
        override fun observeAccountById(accountId: Long): Flow<Account?> = flowOf(account)
        override suspend fun getDefaultAccount(): Account? = account
        override fun observeDefaultAccount(): Flow<Account?> = flowOf(account)
        override suspend fun hasYieldingAccount(): Boolean = false
        override fun observeHasYieldingAccount(): Flow<Boolean> = flowOf(false)
        override suspend fun getAccountCount(): Int = 1
        override suspend fun insert(account: Account): Long = 0L
        override suspend fun update(account: Account) = Unit
        override suspend fun delete(account: Account) = Unit
        override suspend fun reopen(accountId: Long) = Unit
    }

    private val creditCardRepository = object : ICreditCardRepository {
        override fun observeAllCreditCards(): Flow<List<CreditCard>> = flowOf(emptyList())
        override suspend fun getAllCreditCards(): List<CreditCard> = emptyList()
        override suspend fun getAllCreditCardsIncludingClosed(): List<CreditCard> = emptyList()
        override fun observeAllCreditCardsIncludingClosed(): Flow<List<CreditCard>> = flowOf(emptyList())
        override suspend fun getCreditCardById(creditCardId: Long): CreditCard? = null
        override fun observeCreditCardById(creditCardId: Long): Flow<CreditCard?> = flowOf(null)
        override suspend fun insert(creditCard: CreditCard): Long = 0L
        override suspend fun update(creditCard: CreditCard) = Unit
        override suspend fun delete(creditCard: CreditCard) = Unit
        override suspend fun unarchive(accountId: Long) = Unit
    }

    private val analytics = object : Analytics {
        override fun logEvent(event: Event) = Unit
        override fun logScreenView(screenName: String) = Unit
        override fun setUserId(id: String?) = Unit
    }

    private fun submitEditOf(isArchived: Boolean): FakeRecurringRepository {
        val repository = FakeRecurringRepository()
        val editing = recurring(id = 7L, isArchived = isArchived).copy(account = account)

        val vm = RecurringFormViewModel(
            recurring = editing,
            categoryRepository = categoryRepository,
            accountRepository = accountRepository,
            creditCardRepository = creditCardRepository,
            saveRecurringUseCase = SaveRecurringUseCase(repository),
            modalManager = ModalManager(),
            analytics = analytics,
            crashlytics = FakeCrashlytics(),
        )

        vm.onAction(
            RecurringFormAction.Submit(
                RecurringForm(
                    type = TransactionType.EXPENSE,
                    amount = "150,00",
                    title = "Rent",
                    dayOfMonth = "5",
                    category = null,
                    account = account,
                    creditCard = null,
                )
            )
        )

        return repository
    }

    @Test
    fun `saving an archived recurring keeps it archived`() = runTest(dispatcher) {
        val repository = submitEditOf(isArchived = true)
        advanceUntilIdle()

        assertEquals(listOf(true), repository.updated.map { it.isArchived })
    }

    @Test
    fun `saving an unarchived recurring keeps it unarchived`() = runTest(dispatcher) {
        val repository = submitEditOf(isArchived = false)
        advanceUntilIdle()

        assertEquals(listOf(false), repository.updated.map { it.isArchived })
    }
}
