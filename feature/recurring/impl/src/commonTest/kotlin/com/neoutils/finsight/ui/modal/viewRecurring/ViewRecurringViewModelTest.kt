@file:OptIn(ExperimentalCoroutinesApi::class)

package com.neoutils.finsight.ui.modal.viewRecurring

import app.cash.turbine.test
import app.cash.turbine.turbineScope
import com.neoutils.finsight.FakeAccountRepository
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.exception.DetailNotFoundException
import com.neoutils.finsight.domain.model.Budget
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.domain.repository.IBudgetRepository
import com.neoutils.finsight.domain.repository.IRecurringRepository
import com.neoutils.finsight.domain.usecase.ResolveRecurringRetirabilityUseCase
import com.neoutils.finsight.ui.model.RetireAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ViewRecurringViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private class FakeCrashlytics : Crashlytics {
        val recorded = mutableListOf<Throwable>()
        override fun setUserId(id: String?) = Unit
        override fun recordException(e: Throwable) { recorded += e }
    }

    private class FakeRecurringRepository(
        private val hasTransaction: Boolean = false,
    ) : IRecurringRepository {
        private val byId = MutableSharedFlow<Recurring?>(replay = 1)
        fun emit(recurring: Recurring?) { byId.tryEmit(recurring) }
        override fun observeRecurringById(id: Long): Flow<Recurring?> = byId
        override suspend fun getRecurringById(id: Long): Recurring? = null
        override suspend fun hasRecurringForAccount(accountId: Long) = false
        override suspend fun hasRecurringForCreditCard(creditCardId: Long) = false
        override suspend fun hasRecurringForCategory(categoryId: Long) = false
        override suspend fun hasTransactionForRecurring(recurringId: Long) = hasTransaction
        override fun observeAllRecurring(): Flow<List<Recurring>> = throw NotImplementedError()
        override suspend fun insert(recurring: Recurring) = throw NotImplementedError()
        override suspend fun update(recurring: Recurring) = throw NotImplementedError()
        override suspend fun delete(recurring: Recurring) = throw NotImplementedError()
    }

    private fun resolver(hasTransaction: Boolean = false) = ResolveRecurringRetirabilityUseCase(
        recurringRepository = FakeRecurringRepository(hasTransaction = hasTransaction),
        budgetRepository = object : IBudgetRepository {
            override fun observeAllBudgets(): Flow<List<Budget>> = throw NotImplementedError()
            override suspend fun getAllBudgets(): List<Budget> = emptyList()
            override suspend fun insert(budget: Budget) = throw NotImplementedError()
            override suspend fun update(budget: Budget) = throw NotImplementedError()
            override suspend fun delete(budget: Budget) = throw NotImplementedError()
            override suspend fun hasBudgetForCategory(categoryId: Long) = false
            override suspend fun hasBudgetForRecurring(recurringId: Long) = false
        },
    )

    private fun viewModel(
        repository: FakeRecurringRepository,
        hasTransaction: Boolean = false,
        crashlytics: Crashlytics = FakeCrashlytics(),
    ) = ViewRecurringViewModel(
        recurringId = 1L,
        recurringRepository = repository,
        accountRepository = FakeAccountRepository(),
        resolveRetirability = resolver(hasTransaction = hasTransaction),
        crashlytics = crashlytics,
    )

    private fun recurring(id: Long = 1L, amount: Double = 100.0, isArchived: Boolean = false) = Recurring(
        id = id,
        type = TransactionType.EXPENSE,
        amount = amount,
        title = "Rec $id",
        dayOfMonth = 5,
        category = null,
        account = null,
        creditCard = null,
        createdAt = 0L,
        isArchived = isArchived,
    )

    @Test
    fun loadingThenContent() = runTest(dispatcher) {
        val repository = FakeRecurringRepository()
        val vm = viewModel(repository)

        vm.uiState.test {
            assertEquals(ViewRecurringUiState.Loading, awaitItem())
            repository.emit(recurring(id = 1L, amount = 100.0))
            assertEquals(100.0, assertIs<ViewRecurringUiState.Content>(awaitItem()).recurring.amount)
        }
    }

    @Test
    fun firstEmissionNullShowsErrorAndRecordsException() = runTest(dispatcher) {
        val repository = FakeRecurringRepository()
        val crashlytics = FakeCrashlytics()
        val vm = viewModel(repository, crashlytics = crashlytics)

        vm.uiState.test {
            assertEquals(ViewRecurringUiState.Loading, awaitItem())
            repository.emit(null)
            assertEquals(ViewRecurringUiState.Error, awaitItem())
        }

        assertEquals(1, crashlytics.recorded.size)
        assertTrue(crashlytics.recorded.first() is DetailNotFoundException)
    }

    @Test
    fun deletionAfterContentEmitsDismiss() = runTest(dispatcher) {
        val repository = FakeRecurringRepository()
        val vm = viewModel(repository)

        turbineScope {
            val state = vm.uiState.testIn(backgroundScope)
            val events = vm.events.testIn(backgroundScope)

            assertEquals(ViewRecurringUiState.Loading, state.awaitItem())
            repository.emit(recurring(id = 1L))
            assertIs<ViewRecurringUiState.Content>(state.awaitItem())

            repository.emit(null)
            assertIs<ViewRecurringEvent.Dismiss>(events.awaitItem())
            state.expectNoEvents()

            state.cancel()
            events.cancel()
        }
    }

    @Test
    fun `an unused recurring offers delete`() = runTest(dispatcher) {
        val repository = FakeRecurringRepository()
        val vm = viewModel(repository)

        vm.uiState.test {
            assertEquals(ViewRecurringUiState.Loading, awaitItem())
            repository.emit(recurring())
            assertEquals(RetireAction.DELETE, assertIs<ViewRecurringUiState.Content>(awaitItem()).retireAction)
        }
    }

    @Test
    fun `a recurring that generated entries offers archive instead`() = runTest(dispatcher) {
        val repository = FakeRecurringRepository(hasTransaction = true)
        val vm = viewModel(repository, hasTransaction = true)

        vm.uiState.test {
            assertEquals(ViewRecurringUiState.Loading, awaitItem())
            repository.emit(recurring())
            // The screen never offers a delete the domain refuses: the same resolver
            // the delete use case consumes says archive, so archive is what it shows.
            assertEquals(RetireAction.ARCHIVE, assertIs<ViewRecurringUiState.Content>(awaitItem()).retireAction)
        }
    }
}
