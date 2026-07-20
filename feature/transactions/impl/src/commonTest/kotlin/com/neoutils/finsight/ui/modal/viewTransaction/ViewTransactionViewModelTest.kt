@file:OptIn(ExperimentalCoroutinesApi::class)

package com.neoutils.finsight.ui.modal.viewTransaction

import app.cash.turbine.test
import app.cash.turbine.turbineScope
import com.neoutils.finsight.domain.exception.DetailNotFoundException
import com.neoutils.finsight.ui.modal.FakeCrashlytics
import com.neoutils.finsight.ui.modal.FakeTransactionRepository
import com.neoutils.finsight.ui.modal.transaction
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

class ViewTransactionViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        repository: FakeTransactionRepository,
        crashlytics: FakeCrashlytics = FakeCrashlytics(),
    ) = ViewTransactionViewModel(
        transactionId = 1L,
        perspective = null,
        transactionRepository = repository,
        crashlytics = crashlytics,
    )

    @Test
    fun loadingThenContent() = runTest(dispatcher) {
        val repository = FakeTransactionRepository()
        val vm = viewModel(repository)

        vm.uiState.test {
            assertEquals(ViewTransactionUiState.Loading, awaitItem())
            repository.emit(transaction(id = 1L, amount = 100.0))
            val content = assertIs<ViewTransactionUiState.Content>(awaitItem())
            assertEquals(100.0, content.amount)
        }
    }

    @Test
    fun editReemitsContentInPlace() = runTest(dispatcher) {
        val repository = FakeTransactionRepository()
        val vm = viewModel(repository)

        vm.uiState.test {
            assertEquals(ViewTransactionUiState.Loading, awaitItem())
            repository.emit(transaction(id = 1L, amount = 100.0))
            assertEquals(100.0, assertIs<ViewTransactionUiState.Content>(awaitItem()).amount)
            repository.emit(transaction(id = 1L, amount = 250.0))
            assertEquals(250.0, assertIs<ViewTransactionUiState.Content>(awaitItem()).amount)
        }
    }

    @Test
    fun firstEmissionNullShowsErrorAndRecordsException() = runTest(dispatcher) {
        val repository = FakeTransactionRepository()
        val crashlytics = FakeCrashlytics()
        val vm = viewModel(repository, crashlytics)

        vm.uiState.test {
            assertEquals(ViewTransactionUiState.Loading, awaitItem())
            repository.emit(null)
            assertEquals(ViewTransactionUiState.Error, awaitItem())
        }

        assertEquals(1, crashlytics.recorded.size)
        assertTrue(crashlytics.recorded.first() is DetailNotFoundException)
    }

    @Test
    fun deletionAfterContentKeepsContentAndEmitsDismiss() = runTest(dispatcher) {
        val repository = FakeTransactionRepository()
        val crashlytics = FakeCrashlytics()
        val vm = viewModel(repository, crashlytics)

        turbineScope {
            val state = vm.uiState.testIn(backgroundScope)
            val events = vm.events.testIn(backgroundScope)

            assertEquals(ViewTransactionUiState.Loading, state.awaitItem())
            repository.emit(transaction(id = 1L, amount = 100.0))
            assertIs<ViewTransactionUiState.Content>(state.awaitItem())

            repository.emit(null)
            assertIs<ViewTransactionEvent.Dismiss>(events.awaitItem())
            // uiState keeps the last Content — no further state emission
            state.expectNoEvents()

            state.cancel()
            events.cancel()
        }

        assertEquals(0, crashlytics.recorded.size)
    }
}
