@file:OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)

package com.neoutils.finsight.ui.modal.exchangeRateForm

import androidx.compose.runtime.Composable
import com.neoutils.finsight.domain.model.ExchangeRate
import com.neoutils.finsight.domain.repository.IBaseCurrencyRepository
import com.neoutils.finsight.domain.repository.IExchangeRateRepository
import com.neoutils.finsight.ui.component.Modal
import com.neoutils.finsight.ui.component.ModalManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime

/**
 * **The write owns the dismissal, and this is what says so.**
 *
 * Dismissing a `ModalBottomSheet` clears its `ViewModelStore`, which cancels
 * [androidx.lifecycle.viewModelScope]. A button that submits *and* dismisses therefore
 * cancels its own write at the first suspension point — the rate never reaches the
 * archive, and nothing reports it. So the dismissal happens here, after the write
 * returns, exactly as every other form of this app does it.
 *
 * The tests hold the repository suspended to prove the order rather than the outcome:
 * an assertion that the rate was saved would pass just as well if the dismissal came
 * first and the write happened to escape the cancellation.
 */
class ExchangeRateFormViewModelTest {

    private val date = LocalDate(2026, 8, 1)

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `saving dismisses only after the write returns`() = runTest {
        val repository = FakeExchangeRateRepository()
        val modal = RecordingModal()
        val manager = ModalManager().apply { show(modal) }

        val viewModel = viewModel(repository, manager)
        viewModel.onAction(ExchangeRateFormAction.ChangeRate(5.4))
        viewModel.onAction(ExchangeRateFormAction.Submit)

        assertFalse(modal.isDismissed, "dismissed before the write returned")

        repository.release()

        assertTrue(modal.isDismissed)
        assertEquals(5.4, repository.saved.single().rate)
        assertEquals(ExchangeRate.Source.USER, repository.saved.single().source)
    }

    @Test
    fun `removing dismisses only after the write returns`() = runTest {
        val existing = ExchangeRate(
            id = 1,
            currency = "USD",
            date = date,
            rate = 5.4,
            source = ExchangeRate.Source.USER,
        )
        val repository = FakeExchangeRateRepository()
        val modal = RecordingModal()
        val manager = ModalManager().apply { show(modal) }

        viewModel(repository, manager, existing).onAction(ExchangeRateFormAction.Remove)

        assertFalse(modal.isDismissed, "dismissed before the write returned")

        repository.release()

        assertTrue(modal.isDismissed)
        assertEquals(listOf(existing), repository.removed)
    }

    /** A form that cannot state a rate has nothing to write, and nothing to dismiss for. */
    @Test
    fun `a form without a rate neither writes nor dismisses`() = runTest {
        val repository = FakeExchangeRateRepository()
        val modal = RecordingModal()
        val manager = ModalManager().apply { show(modal) }

        viewModel(repository, manager).onAction(ExchangeRateFormAction.Submit)

        assertTrue(repository.saved.isEmpty())
        assertFalse(modal.isDismissed)
    }

    private fun viewModel(
        repository: IExchangeRateRepository,
        manager: ModalManager,
        existing: ExchangeRate? = null,
    ) = ExchangeRateFormViewModel(
        existing = existing,
        baseCurrencyRepository = FakeBaseCurrencyRepository(),
        exchangeRateRepository = repository,
        modalManager = manager,
    )
}

private class RecordingModal : Modal() {

    var isDismissed = false
        private set

    override fun onDismissed() {
        isDismissed = true
    }

    @Composable
    override fun Content() = Unit
}

private class FakeBaseCurrencyRepository : IBaseCurrencyRepository {
    private val flow = MutableStateFlow("BRL")
    override fun observe(): StateFlow<String> = flow
}

/** Suspends every write until [release], so the order of the two steps is observable. */
private class FakeExchangeRateRepository : IExchangeRateRepository {

    private val gate = CompletableDeferred<Unit>()

    val saved = mutableListOf<ExchangeRate>()
    val removed = mutableListOf<ExchangeRate>()

    fun release() = gate.complete(Unit)

    override suspend fun rateAsOf(currency: String, date: LocalDate): ExchangeRate? = null

    override suspend fun ratesAsOf(date: LocalDate): Map<String, ExchangeRate> = emptyMap()

    override fun observeAll(): Flow<List<ExchangeRate>> = MutableStateFlow(emptyList())

    override suspend fun save(rate: ExchangeRate) {
        gate.await()
        saved += rate
    }

    override suspend fun remove(rate: ExchangeRate) {
        gate.await()
        removed += rate
    }
}
