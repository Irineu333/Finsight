@file:OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)

package com.neoutils.finsight.ui.modal.exchangeRateForm

import androidx.compose.runtime.Composable
import com.neoutils.finsight.FakeCurrencyRepository
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
            counterCurrency = "BRL",
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

    /**
     * Both ends are stated, in the direction the user chose, and neither is ordered nor
     * inverted on the way to the archive (design D2).
     */
    @Test
    fun `the pair is written as chosen, and never canonicalised`() = runTest {
        val repository = FakeExchangeRateRepository()
        val manager = ModalManager().apply { show(RecordingModal()) }

        val viewModel = viewModel(repository, manager)
        viewModel.onAction(ExchangeRateFormAction.SelectFrom("BRL"))
        viewModel.onAction(ExchangeRateFormAction.SelectTo("USD"))
        viewModel.onAction(ExchangeRateFormAction.ChangeRate(0.18))
        viewModel.onAction(ExchangeRateFormAction.Submit)
        repository.release()

        val saved = repository.saved.single()
        assertEquals("BRL", saved.currency)
        assertEquals("USD", saved.counterCurrency)
        assertEquals(0.18, saved.rate)
    }

    /** The base in force is the counterpart a new observation starts with. */
    @Test
    fun `a new rate starts with the base as its counterpart`() = runTest {
        val state = viewModel(FakeExchangeRateRepository(), ModalManager()).uiState.value

        assertEquals("BRL", state.to)
        assertTrue(state.from != "BRL")
    }

    /**
     * The whole catalog, on both ends: pricing the base against another currency is a
     * legitimate observation whose inverse feeds the reading.
     */
    @Test
    fun `the base is offered on both ends`() = runTest {
        val state = viewModel(FakeExchangeRateRepository(), ModalManager()).uiState.value

        assertTrue(state.selectableCurrencies.any { it.code == "BRL" })
    }

    @Test
    fun `a currency against itself cannot be submitted`() = runTest {
        val viewModel = viewModel(FakeExchangeRateRepository(), ModalManager())
        viewModel.onAction(ExchangeRateFormAction.ChangeRate(1.0))
        viewModel.onAction(ExchangeRateFormAction.SelectFrom("BRL"))

        assertFalse(viewModel.uiState.value.canSubmit, "from == to is the one restriction left")

        viewModel.onAction(ExchangeRateFormAction.SelectFrom("USD"))

        assertTrue(viewModel.uiState.value.canSubmit)
    }

    /** Editing opens in the direction the observation was made in. */
    @Test
    fun `editing opens the pair in the direction it was observed`() = runTest {
        val existing = ExchangeRate(
            id = 1,
            currency = "BRL",
            counterCurrency = "USD",
            date = date,
            rate = 0.18,
            source = ExchangeRate.Source.USER,
        )

        val state = viewModel(FakeExchangeRateRepository(), ModalManager(), existing).uiState.value

        assertEquals("BRL", state.from)
        assertEquals("USD", state.to)
    }

    private fun viewModel(
        repository: IExchangeRateRepository,
        manager: ModalManager,
        existing: ExchangeRate? = null,
    ) = ExchangeRateFormViewModel(
        existing = existing,
        baseCurrencyRepository = FakeBaseCurrencyRepository(),
        exchangeRateRepository = repository,
        currencyRepository = FakeCurrencyRepository(),
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
    override suspend fun set(code: String) { flow.value = code }
}

/** Suspends every write until [release], so the order of the two steps is observable. */
private class FakeExchangeRateRepository : IExchangeRateRepository {

    private val gate = CompletableDeferred<Unit>()

    val saved = mutableListOf<ExchangeRate>()
    val removed = mutableListOf<ExchangeRate>()

    fun release() = gate.complete(Unit)

    override suspend fun rateAsOf(currency: String, date: LocalDate): ExchangeRate? = null

    override suspend fun ratesAsOf(date: LocalDate): Map<String, ExchangeRate> = emptyMap()

    override suspend fun rateBetween(from: String, to: String, date: LocalDate): ExchangeRate? = null

    override fun observeAll(): Flow<List<ExchangeRate>> = MutableStateFlow(emptyList())

    override suspend fun save(rate: ExchangeRate) {
        gate.await()
        saved += rate
    }

    override suspend fun remove(rate: ExchangeRate) {
        gate.await()
        removed += rate
    }
    override suspend fun countNaming(currency: String) = 0
    override suspend fun removeAllNaming(currency: String) = Unit
}
