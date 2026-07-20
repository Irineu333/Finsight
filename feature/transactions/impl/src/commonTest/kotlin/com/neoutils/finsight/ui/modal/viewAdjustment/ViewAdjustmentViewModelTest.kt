@file:OptIn(ExperimentalCoroutinesApi::class)

package com.neoutils.finsight.ui.modal.viewAdjustment

import app.cash.turbine.test
import app.cash.turbine.turbineScope
import com.neoutils.finsight.domain.exception.DetailNotFoundException
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.ui.modal.FakeCrashlytics
import com.neoutils.finsight.ui.modal.FakeOperationRepository
import com.neoutils.finsight.ui.modal.operation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

class ViewAdjustmentViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        repository: FakeOperationRepository,
        crashlytics: FakeCrashlytics = FakeCrashlytics(),
    ) = ViewAdjustmentViewModel(
        transactionId = 1L,
        operationRepository = repository,
        crashlytics = crashlytics,
    )

    @Test
    fun loadingThenContent() = runTest(dispatcher) {
        val repository = FakeOperationRepository()
        val vm = viewModel(repository)

        vm.uiState.test {
            assertEquals(ViewAdjustmentUiState.Loading, awaitItem())
            repository.emit(operation(id = 1L, amount = 42.0, type = TransactionType.ADJUSTMENT))
            assertEquals(42.0, assertIs<ViewAdjustmentUiState.Content>(awaitItem()).signedAmount)
        }
    }

    @Test
    fun firstEmissionNullShowsErrorAndRecordsException() = runTest(dispatcher) {
        val repository = FakeOperationRepository()
        val crashlytics = FakeCrashlytics()
        val vm = viewModel(repository, crashlytics)

        vm.uiState.test {
            assertEquals(ViewAdjustmentUiState.Loading, awaitItem())
            repository.emit(null)
            assertEquals(ViewAdjustmentUiState.Error, awaitItem())
        }

        assertEquals(1, crashlytics.recorded.size)
        assertTrue(crashlytics.recorded.first() is DetailNotFoundException)
    }

    @Test
    fun deletionAfterContentEmitsDismiss() = runTest(dispatcher) {
        val repository = FakeOperationRepository()
        val vm = viewModel(repository)

        turbineScope {
            val state = vm.uiState.testIn(backgroundScope)
            val events = vm.events.testIn(backgroundScope)

            assertEquals(ViewAdjustmentUiState.Loading, state.awaitItem())
            repository.emit(operation(id = 1L, type = TransactionType.ADJUSTMENT))
            assertIs<ViewAdjustmentUiState.Content>(state.awaitItem())

            repository.emit(null)
            assertIs<ViewAdjustmentEvent.Dismiss>(events.awaitItem())
            state.expectNoEvents()

            state.cancel()
            events.cancel()
        }
    }
}
