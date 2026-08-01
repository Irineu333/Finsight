@file:OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)

package com.neoutils.finsight

import app.cash.turbine.test
import com.neoutils.finsight.domain.model.ExchangeRate
import com.neoutils.finsight.domain.repository.IBaseCurrencyRepository
import com.neoutils.finsight.domain.repository.IExchangeRateRepository
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

            assertTrue(state.rates.single { it.rate.currency == "USD" }.isOutdated)
            assertFalse(state.rates.single { it.rate.currency == "EUR" }.isOutdated)
        }
    }

    /** Exactly thirty days is still current: the threshold is "older than". */
    @Test
    fun `the threshold is exclusive`() = runTest {
        val rates = listOf(rate("USD", today.minus(30, DateTimeUnit.DAY)))

        viewModel(rates).uiState.test {
            val state = awaitItem().takeIf { !it.isLoading } ?: awaitItem()

            assertFalse(state.rates.single().isOutdated)
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

    private fun rate(currency: String, date: LocalDate) = ExchangeRate(
        id = currency.hashCode().toLong(),
        currency = currency,
        date = date,
        rate = 5.5,
        source = ExchangeRate.Source.DERIVED,
    )
}

private class FakeBaseCurrencyRepository(base: String) : IBaseCurrencyRepository {
    private val flow = MutableStateFlow(base)
    override fun observe(): StateFlow<String> = flow
}

private class FakeExchangeRateRepository(
    rates: List<ExchangeRate>,
) : IExchangeRateRepository {

    private val all = MutableStateFlow(rates)

    override suspend fun rateAsOf(currency: String, date: LocalDate) =
        all.value.filter { it.currency == currency && it.date <= date }.maxByOrNull { it.date }

    override suspend fun ratesAsOf(date: LocalDate) =
        all.value.filter { it.date <= date }.associateBy { it.currency }

    override fun observeAll(): Flow<List<ExchangeRate>> = all

    override suspend fun save(rate: ExchangeRate) {
        all.value = all.value.filterNot { it.id == rate.id } + rate
    }

    override suspend fun remove(rate: ExchangeRate) {
        all.value = all.value.filterNot { it.id == rate.id }
    }
}
