@file:OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)

package com.neoutils.finsight

import app.cash.turbine.test
import com.neoutils.finsight.domain.model.ExchangeRate
import com.neoutils.finsight.domain.repository.IExchangeRateRepository
import com.neoutils.finsight.ui.screen.exchangeRateHistory.ExchangeRateHistoryUiState
import com.neoutils.finsight.ui.screen.exchangeRateHistory.ExchangeRateHistoryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** The history: what it groups, in what order, and what each filter narrows. */
class ExchangeRateHistoryViewModelTest {

    private val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    private val march = LocalDate(2026, 3, 14)
    private val february = LocalDate(2026, 2, 1)
    private val january = LocalDate(2026, 1, 5)

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private var nextId = 1L

    private fun rate(
        currency: String,
        counterCurrency: String = "BRL",
        date: LocalDate = march,
        source: ExchangeRate.Source = ExchangeRate.Source.USER,
        value: Double = 5.5,
    ) = ExchangeRate(
        id = nextId++,
        currency = currency,
        counterCurrency = counterCurrency,
        date = date,
        rate = value,
        source = source,
    )

    private class FakeArchive(rates: List<ExchangeRate>) : IExchangeRateRepository {
        private val flow = MutableStateFlow(rates)
        override suspend fun rateAsOf(currency: String, date: LocalDate): ExchangeRate? = null
        override suspend fun ratesAsOf(date: LocalDate) = emptyMap<String, ExchangeRate>()
        override suspend fun rateBetween(from: String, to: String, date: LocalDate): ExchangeRate? = null
        override fun observeAll(): Flow<List<ExchangeRate>> = flow
        override suspend fun save(rate: ExchangeRate) = Unit
        override suspend fun remove(rate: ExchangeRate) = Unit
        override suspend fun countNaming(currency: String) = 0
        override suspend fun removeAllNaming(currency: String) = Unit
    }

    private fun viewModel(rates: List<ExchangeRate>, initialCurrency: String? = null) =
        ExchangeRateHistoryViewModel(
            initialCurrency = initialCurrency,
            exchangeRateRepository = FakeArchive(rates),
        )

    private suspend fun ExchangeRateHistoryViewModel.loaded(): ExchangeRateHistoryUiState {
        var state = uiState.value
        uiState.test {
            state = awaitItem().takeIf { !it.isLoading } ?: awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        return state
    }

    private val ExchangeRateHistoryUiState.rates get() = groups.flatMap { it.rates }.map { it.rate }

    @Test
    fun `groups are keyed by the counterpart currency and ordered by their newest`() = runTest {
        val viewModel = viewModel(
            listOf(
                rate("EUR", counterCurrency = "BRL", date = february),
                rate("JPY", counterCurrency = "USD", date = march),
                rate("USD", counterCurrency = "BRL", date = january),
            )
        )

        val state = viewModel.loaded()

        assertEquals(listOf("USD", "BRL"), state.groups.map { it.counterCurrency })
        assertEquals(2, state.groups.single { it.counterCurrency == "BRL" }.rates.size)
    }

    /** Two observations, not one shown backwards: each sits under its own heading. */
    @Test
    fun `the same pair in both directions appears in two groups`() = runTest {
        val state = viewModel(
            listOf(
                rate("USD", counterCurrency = "BRL"),
                rate("BRL", counterCurrency = "USD", value = 0.18),
            )
        ).loaded()

        assertEquals(setOf("BRL", "USD"), state.groups.map { it.counterCurrency }.toSet())
    }

    @Test
    fun `filtering by currency keeps the observations naming it on either end`() = runTest {
        val viewModel = viewModel(
            listOf(
                rate("USD", counterCurrency = "BRL"),
                rate("BRL", counterCurrency = "USD", value = 0.18),
                rate("EUR", counterCurrency = "BRL"),
            )
        )

        viewModel.onFilterByCurrency("USD")

        assertEquals(
            setOf("USD" to "BRL", "BRL" to "USD"),
            viewModel.loaded().rates.map { it.currency to it.counterCurrency }.toSet(),
        )
    }

    @Test
    fun `filtering by source keeps only that origin`() = runTest {
        val viewModel = viewModel(
            listOf(
                rate("USD", source = ExchangeRate.Source.USER),
                rate("EUR", source = ExchangeRate.Source.REMOTE),
                rate("JPY", source = ExchangeRate.Source.DERIVED),
            )
        )

        viewModel.onFilterBySource(ExchangeRate.Source.USER)

        assertEquals(listOf("USD"), viewModel.loaded().rates.map { it.currency })
    }

    @Test
    fun `filtering by date keeps only the interval`() = runTest {
        val viewModel = viewModel(
            listOf(
                rate("USD", date = january),
                rate("EUR", date = february),
                rate("JPY", date = march),
            )
        )

        viewModel.onFilterByDate(start = february, end = march)

        assertEquals(setOf("EUR", "JPY"), viewModel.loaded().rates.map { it.currency }.toSet())
    }

    @Test
    fun `the three filters compose`() = runTest {
        val viewModel = viewModel(
            listOf(
                rate("USD", date = february, source = ExchangeRate.Source.REMOTE),
                rate("USD", date = march, source = ExchangeRate.Source.REMOTE),
                rate("USD", date = february, source = ExchangeRate.Source.USER),
                rate("EUR", date = february, source = ExchangeRate.Source.REMOTE),
            )
        )

        viewModel.onFilterByCurrency("USD")
        viewModel.onFilterBySource(ExchangeRate.Source.REMOTE)
        viewModel.onFilterByDate(start = february, end = february)

        val kept = viewModel.loaded().rates.single()
        assertEquals("USD", kept.currency)
        assertEquals(february, kept.date)
        assertEquals(ExchangeRate.Source.REMOTE, kept.source)
    }

    /** Reached from a row of the in-force view, the history arrives already narrowed. */
    @Test
    fun `arriving from a pair pre-filters by its currency`() = runTest {
        val state = viewModel(
            listOf(rate("USD"), rate("EUR")),
            initialCurrency = "USD",
        ).loaded()

        assertEquals(listOf("USD"), state.rates.map { it.currency })
        assertEquals("USD", state.filters.currency)
    }

    /** A filter that only offered what survives the current one could not be widened. */
    @Test
    fun `the currency filter offers every currency of the archive, not of the view`() = runTest {
        val viewModel = viewModel(listOf(rate("USD"), rate("EUR")))

        viewModel.onFilterByCurrency("USD")

        assertEquals(listOf("BRL", "EUR", "USD"), viewModel.loaded().currencies)
    }

    @Test
    fun `clearing the filters brings the whole archive back`() = runTest {
        val viewModel = viewModel(listOf(rate("USD"), rate("EUR")), initialCurrency = "USD")

        viewModel.onClearFilters()

        assertEquals(2, viewModel.loaded().rates.size)
    }

    @Test
    fun `an outdated observation is flagged and a recent one is not`() = runTest {
        val state = viewModel(
            listOf(
                rate("USD", date = today),
                rate("EUR", date = LocalDate(2020, 1, 1)),
            )
        ).loaded()

        val items = state.groups.flatMap { it.rates }
        assertEquals(true, items.single { it.rate.currency == "EUR" }.isOutdated)
        assertEquals(false, items.single { it.rate.currency == "USD" }.isOutdated)
    }
}
