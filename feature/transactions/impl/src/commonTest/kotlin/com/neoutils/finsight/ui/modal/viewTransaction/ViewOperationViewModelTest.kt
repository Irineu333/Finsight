@file:OptIn(ExperimentalCoroutinesApi::class)

package com.neoutils.finsight.ui.modal.viewTransaction

import app.cash.turbine.test
import app.cash.turbine.turbineScope
import com.neoutils.finsight.domain.exception.DetailNotFoundException
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

class ViewOperationViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        repository: FakeOperationRepository,
        crashlytics: FakeCrashlytics = FakeCrashlytics(),
    ) = ViewOperationViewModel(
        transactionId = 1L,
        perspective = null,
        operationRepository = repository,
        crashlytics = crashlytics,
    )

    @Test
    fun loadingThenContent() = runTest(dispatcher) {
        val repository = FakeOperationRepository()
        val vm = viewModel(repository)

        vm.uiState.test {
            assertEquals(ViewOperationUiState.Loading, awaitItem())
            repository.emit(operation(id = 1L, amount = 100.0))
            val content = assertIs<ViewOperationUiState.Content>(awaitItem())
            assertEquals(100.0, content.transaction.amount)
        }
    }

    @Test
    fun editReemitsContentInPlace() = runTest(dispatcher) {
        val repository = FakeOperationRepository()
        val vm = viewModel(repository)

        vm.uiState.test {
            assertEquals(ViewOperationUiState.Loading, awaitItem())
            repository.emit(operation(id = 1L, amount = 100.0))
            assertEquals(100.0, assertIs<ViewOperationUiState.Content>(awaitItem()).transaction.amount)
            repository.emit(operation(id = 1L, amount = 250.0))
            assertEquals(250.0, assertIs<ViewOperationUiState.Content>(awaitItem()).transaction.amount)
        }
    }

    @Test
    fun firstEmissionNullShowsErrorAndRecordsException() = runTest(dispatcher) {
        val repository = FakeOperationRepository()
        val crashlytics = FakeCrashlytics()
        val vm = viewModel(repository, crashlytics)

        vm.uiState.test {
            assertEquals(ViewOperationUiState.Loading, awaitItem())
            repository.emit(null)
            assertEquals(ViewOperationUiState.Error, awaitItem())
        }

        assertEquals(1, crashlytics.recorded.size)
        assertTrue(crashlytics.recorded.first() is DetailNotFoundException)
    }

    @Test
    fun deletionAfterContentKeepsContentAndEmitsDismiss() = runTest(dispatcher) {
        val repository = FakeOperationRepository()
        val crashlytics = FakeCrashlytics()
        val vm = viewModel(repository, crashlytics)

        turbineScope {
            val state = vm.uiState.testIn(backgroundScope)
            val events = vm.events.testIn(backgroundScope)

            assertEquals(ViewOperationUiState.Loading, state.awaitItem())
            repository.emit(operation(id = 1L, amount = 100.0))
            assertIs<ViewOperationUiState.Content>(state.awaitItem())

            repository.emit(null)
            assertIs<ViewOperationEvent.Dismiss>(events.awaitItem())
            // uiState keeps the last Content — no further state emission
            state.expectNoEvents()

            state.cancel()
            events.cancel()
        }

        assertEquals(0, crashlytics.recorded.size)
    }
}
