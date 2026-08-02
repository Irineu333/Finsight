@file:OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)

package com.neoutils.finsight

import app.cash.turbine.test
import com.neoutils.finsight.domain.model.ExchangeRate
import com.neoutils.finsight.domain.repository.IBaseCurrencyRepository
import com.neoutils.finsight.domain.repository.IExchangeRateRepository
import com.neoutils.finsight.ui.screen.exchangeRates.ExchangeRatesUiState
import com.neoutils.finsight.ui.screen.exchangeRates.ExchangeRatesViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class ExchangeRatesViewModelTest {

    private val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `a rate older than thirty days is flagged, a recent one is not`() = runTest {
        val rates = listOf(
            rate("USD", today.minus(31, DateTimeUnit.DAY)),
            rate("EUR", today),
        )

        viewModel(rates).uiState.test {
            val state = awaitItem().takeIf { !it.isLoading } ?: awaitItem()

            assertTrue(state.items.single { it.rate.currency == "USD" }.isOutdated)
            assertFalse(state.items.single { it.rate.currency == "EUR" }.isOutdated)
        }
    }

    /** Exactly thirty days is still current: the threshold is "older than". */
    @Test
    fun `the threshold is exclusive`() = runTest {
        val rates = listOf(rate("USD", today.minus(30, DateTimeUnit.DAY)))

        viewModel(rates).uiState.test {
            val state = awaitItem().takeIf { !it.isLoading } ?: awaitItem()

            assertFalse(state.items.single().isOutdated)
        }
    }

    @Test
    fun `an empty archive is empty rather than loading forever`() = runTest {
        viewModel(emptyList()).uiState.test {
            val state = awaitItem().takeIf { !it.isLoading } ?: awaitItem()

            assertTrue(state.isEmpty)
            assertEquals("BRL", state.baseCurrency)
        }
    }

    private fun viewModel(rates: List<ExchangeRate>) = ExchangeRatesViewModel(
        baseCurrencyRepository = FakeBaseCurrencyRepository("BRL"),
        exchangeRateRepository = FakeExchangeRateRepository(rates),
    )

    /** The observations of every group, flattened — what the per-row assertions ask. */
    private val ExchangeRatesUiState.items get() = groups.flatMap { it.rates }

    /**
     * The grouping key is the currency the rows are priced **in**, and the order of the
     * groups is the natural extension of the archive's `ORDER BY date DESC`.
     */
    @Test
    fun `the archive is grouped by the counterpart currency, newest group first`() = runTest {
        val rates = listOf(
            rate("USD", today.minus(5, DateTimeUnit.DAY)),
            rate("EUR", today.minus(10, DateTimeUnit.DAY)),
            rate("JPY", today, counterCurrency = "USD"),
        )

        viewModel(rates).uiState.test {
            val state = awaitItem().takeIf { !it.isLoading } ?: awaitItem()

            assertEquals(listOf("USD", "BRL"), state.groups.map { it.counterCurrency })
            assertEquals(2, state.groups.single { it.counterCurrency == "BRL" }.rates.size)
        }
    }

    /**
     * The ordinary archive, and the reason the key is this one: everything is priced in
     * the base, so keying on the priced currency would put every row in a group of its
     * own and group nothing.
     */
    @Test
    fun `an archive priced entirely in the base is one group`() = runTest {
        val rates = listOf(
            rate("USD", today),
            rate("EUR", today.minus(1, DateTimeUnit.DAY)),
            rate("JPY", today.minus(2, DateTimeUnit.DAY)),
        )

        viewModel(rates).uiState.test {
            val state = awaitItem().takeIf { !it.isLoading } ?: awaitItem()

            assertEquals(listOf("BRL"), state.groups.map { it.counterCurrency })
            assertEquals(3, state.groups.single().rates.size)
        }
    }

    /**
     * Two directions of the same pair are two distinct observations, and each appears
     * under the currency it prices — never inverted to join the other.
     */
    @Test
    fun `the same pair in both directions appears in two groups`() = runTest {
        val rates = listOf(
            rate("USD", today, counterCurrency = "BRL"),
            rate("BRL", today, counterCurrency = "USD", rate = 0.18),
        )

        viewModel(rates).uiState.test {
            val state = awaitItem().takeIf { !it.isLoading } ?: awaitItem()

            assertEquals(setOf("BRL", "USD"), state.groups.map { it.counterCurrency }.toSet())
            assertEquals(
                0.18,
                state.groups.single { it.counterCurrency == "USD" }.rates.single().rate.rate,
                "shown as observed, never inverted to fit the other group",
            )
        }
    }

    private fun rate(
        currency: String,
        date: LocalDate,
        counterCurrency: String = "BRL",
        rate: Double = 5.5,
    ) = ExchangeRate(
        id = (currency + counterCurrency + date).hashCode().toLong(),
        currency = currency,
        counterCurrency = counterCurrency,
        date = date,
        rate = rate,
        source = ExchangeRate.Source.DERIVED,
    )
}

private class FakeBaseCurrencyRepository(base: String) : IBaseCurrencyRepository {
    private val flow = MutableStateFlow(base)
    override fun observe(): StateFlow<String> = flow
    override suspend fun set(code: String) { flow.value = code }
}

private class FakeExchangeRateRepository(
    rates: List<ExchangeRate>,
) : IExchangeRateRepository {

    private val all = MutableStateFlow(rates)

    override suspend fun rateAsOf(currency: String, date: LocalDate) =
        all.value.filter { it.currency == currency && it.date <= date }.maxByOrNull { it.date }

    override suspend fun ratesAsOf(date: LocalDate) =
        all.value.filter { it.date <= date }.associateBy { it.currency }

    override suspend fun rateBetween(from: String, to: String, date: LocalDate) =
        all.value.filter { it.currency == from && it.counterCurrency == to && it.date <= date }
            .maxByOrNull { it.date }

    override fun observeAll(): Flow<List<ExchangeRate>> = all

    override suspend fun save(rate: ExchangeRate) {
        all.value = all.value.filterNot { it.id == rate.id } + rate
    }

    override suspend fun remove(rate: ExchangeRate) {
        all.value = all.value.filterNot { it.id == rate.id }
    }
}
