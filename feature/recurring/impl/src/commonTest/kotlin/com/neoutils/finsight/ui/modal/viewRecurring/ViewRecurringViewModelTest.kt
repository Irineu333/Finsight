@file:OptIn(ExperimentalCoroutinesApi::class)

package com.neoutils.finsight.ui.modal.viewRecurring

import app.cash.turbine.test
import app.cash.turbine.turbineScope
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.exception.DetailNotFoundException
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.domain.repository.IRecurringRepository
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

    private class FakeRecurringRepository : IRecurringRepository {
        private val byId = MutableSharedFlow<Recurring?>(replay = 1)
        fun emit(recurring: Recurring?) { byId.tryEmit(recurring) }
        override fun observeRecurringById(id: Long): Flow<Recurring?> = byId
        override fun observeAllRecurring(): Flow<List<Recurring>> = throw NotImplementedError()
        override suspend fun insert(recurring: Recurring) = throw NotImplementedError()
        override suspend fun update(recurring: Recurring) = throw NotImplementedError()
        override suspend fun delete(recurring: Recurring) = throw NotImplementedError()
    }

    private fun recurring(id: Long = 1L, amount: Double = 100.0) = Recurring(
        id = id,
        type = TransactionType.EXPENSE,
        amount = amount,
        title = "Rec $id",
        dayOfMonth = 5,
        category = null,
        account = null,
        creditCard = null,
        createdAt = 0L,
    )

    @Test
    fun loadingThenContent() = runTest(dispatcher) {
        val repository = FakeRecurringRepository()
        val vm = ViewRecurringViewModel(recurringId = 1L, recurringRepository = repository, crashlytics = FakeCrashlytics())

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
        val vm = ViewRecurringViewModel(recurringId = 1L, recurringRepository = repository, crashlytics = crashlytics)

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
        val vm = ViewRecurringViewModel(recurringId = 1L, recurringRepository = repository, crashlytics = FakeCrashlytics())

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
}
