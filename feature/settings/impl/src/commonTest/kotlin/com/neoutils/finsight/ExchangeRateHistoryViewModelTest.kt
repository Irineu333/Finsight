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
    fun `the history is partitioned by day, the most recent first`() = runTest {
        val viewModel = viewModel(
            listOf(
                rate("EUR", counterCurrency = "BRL", date = february),
                rate("JPY", counterCurrency = "USD", date = march),
                rate("USD", counterCurrency = "BRL", date = january),
            )
        )

        val state = viewModel.loaded()

        assertEquals(listOf(march, february, january), state.groups.map { it.date })
        assertEquals(1, state.groups.single { it.date == march }.rates.size)
    }

    /**
     * The reason the axis is the date: with a row per pair per day, the ordinary archive —
     * everything priced in the base — would be a single group of hundreds of rows if it
     * were keyed by the counterpart currency.
     */
    @Test
    fun `the ordinary archive does not collapse into one group`() = runTest {
        val days = (1..30).map { LocalDate(2026, 3, it) }
        val state = viewModel(
            days.flatMap { day ->
                listOf(rate("USD", date = day), rate("EUR", date = day), rate("JPY", date = day))
            }
        ).loaded()

        assertEquals(30, state.groups.size)
        assertEquals(3, state.groups.first().rates.size)
    }

    /** Two observations, not one shown backwards — and on one day they share a heading. */
    @Test
    fun `the same pair in both directions appears in the same day, as two rows`() = runTest {
        val state = viewModel(
            listOf(
                rate("USD", counterCurrency = "BRL"),
                rate("BRL", counterCurrency = "USD", value = 0.18),
            )
        ).loaded()

        val day = state.groups.single()
        assertEquals(march, day.date)
        assertEquals(
            // Ordered by the counterpart end first, so the day reads as a block of what is
            // priced in reais and then a block of what is priced in dollars.
            listOf("USD" to "BRL", "BRL" to "USD"),
            day.rates.map { it.rate.currency to it.rate.counterCurrency },
            "each in the direction it was observed in, in a total and stable order",
        )
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

    /**
     * The order within a day is **total and stable**, so two readings of the same archive
     * list the day the same way. It is a property of what the rows are — counterpart, then
     * currency, then id — and not of the order the archive happened to arrive in, which is
     * why the assertion is that a shuffled archive reads identically.
     */
    @Test
    fun `the order within a day does not depend on the order the archive was read in`() = runTest {
        val rates = listOf(
            rate("USD", counterCurrency = "BRL"),
            rate("EUR", counterCurrency = "BRL"),
            rate("JPY", counterCurrency = "USD"),
            rate("BRL", counterCurrency = "USD"),
        )
        val expected = listOf(
            "EUR" to "BRL",
            "USD" to "BRL",
            "BRL" to "USD",
            "JPY" to "USD",
        )

        for (archive in listOf(rates, rates.reversed(), rates.shuffled(kotlin.random.Random(7)))) {
            val day = viewModel(archive).loaded().groups.single()

            assertEquals(
                expected,
                day.rates.map { it.rate.currency to it.rate.counterCurrency },
                "the same day, listed the same way, whatever order the rows came in",
            )
        }
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
