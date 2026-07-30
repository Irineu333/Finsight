package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.ExchangeRate
import com.neoutils.finsight.domain.repository.IBaseCurrencyRepository
import com.neoutils.finsight.domain.repository.IExchangeRateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What a cross-currency operation teaches, and what it may suggest.
 *
 * The two use cases are tested together because they are the two directions of one fact: the
 * rate implied by two typed ends, and the second end implied by a known rate. Getting the two
 * to disagree is the failure that would show as a form suggesting a number the operation then
 * records as a different quote.
 */
class OperationRateTest {

    private val date = LocalDate.parse("2026-05-10")

    @Test
    fun `the two ends of an operation imply the quote, in base per unit`() = runTest {
        val rates = RecordingRates()

        collect(rates)(
            sourceCurrency = "BRL",
            sourceAmount = 550.0,
            destinationCurrency = "USD",
            destinationAmount = 100.0,
            date = date,
        )

        assertEquals(
            listOf(ExchangeRate("USD", date, 5.5, ExchangeRate.Source.OPERATION)),
            rates.recorded,
        )
    }

    @Test
    fun `the direction of the operation does not change the quote`() = runTest {
        val rates = RecordingRates()

        // Dollars leaving for reais: the base is still the numerator, because a rate says
        // what one unit of the *other* currency is worth.
        collect(rates)(
            sourceCurrency = "USD",
            sourceAmount = 100.0,
            destinationCurrency = "BRL",
            destinationAmount = 550.0,
            date = date,
        )

        assertEquals(listOf(5.5), rates.recorded.map { it.rate })
        assertEquals(listOf("USD"), rates.recorded.map { it.currency })
    }

    @Test
    fun `an operation inside one currency teaches nothing`() = runTest {
        val rates = RecordingRates()

        collect(rates)(
            sourceCurrency = "BRL",
            sourceAmount = 100.0,
            destinationCurrency = "BRL",
            destinationAmount = 100.0,
            date = date,
        )

        assertTrue(rates.recorded.isEmpty())
    }

    @Test
    fun `an operation between two non-base currencies teaches nothing`() = runTest {
        val rates = RecordingRates()

        // A cross rate is not a rate against the base, and the app holds no matrix of pairs
        // (design D11). Deriving one from this would invent a number.
        collect(rates)(
            sourceCurrency = "USD",
            sourceAmount = 100.0,
            destinationCurrency = "EUR",
            destinationAmount = 90.0,
            date = date,
        )

        assertTrue(rates.recorded.isEmpty())
    }

    @Test
    fun `an end of zero derives no quote`() = runTest {
        val rates = RecordingRates()

        collect(rates)(
            sourceCurrency = "BRL",
            sourceAmount = 0.0,
            destinationCurrency = "USD",
            destinationAmount = 100.0,
            date = date,
        )

        assertTrue(rates.recorded.isEmpty())
    }

    @Test
    fun `a quote from the operation's own date is the only one that may fill the field`() = runTest {
        val history = listOf(ExchangeRate("USD", date, 5.5, ExchangeRate.Source.USER))

        val suggestion = suggest(history)(
            fromCurrency = "BRL",
            toCurrency = "USD",
            amount = 550.0,
            date = date,
        )

        assertEquals(100.0, suggestion?.amount)
        assertEquals(true, suggestion?.isFromOperationDate)
    }

    @Test
    fun `an older quote still suggests a number, and says it is older`() = runTest {
        val older = LocalDate.parse("2026-05-01")
        val history = listOf(ExchangeRate("USD", older, 5.0, ExchangeRate.Source.OPERATION))

        val suggestion = suggest(history)(
            fromCurrency = "BRL",
            toCurrency = "USD",
            amount = 500.0,
            date = date,
        )

        // The value is real and the date is the quote's own: it is what tells the form to
        // offer it as a placeholder rather than type it in, so that an old rate is never
        // recorded back as a new one.
        assertEquals(100.0, suggestion?.amount)
        assertEquals(older, suggestion?.rate?.date)
        assertEquals(false, suggestion?.isFromOperationDate)
    }

    @Test
    fun `arriving at the base multiplies, leaving it divides`() = runTest {
        val history = listOf(ExchangeRate("USD", date, 5.5, ExchangeRate.Source.USER))

        assertEquals(
            550.0,
            suggest(history)(fromCurrency = "USD", toCurrency = "BRL", amount = 100.0, date = date)?.amount,
        )
    }

    @Test
    fun `nothing is suggested where nothing can be`() = runTest {
        val history = listOf(ExchangeRate("USD", date, 5.5, ExchangeRate.Source.USER))

        assertNull(suggest(history)("BRL", "BRL", 100.0, date), "one currency, nothing to convert")
        assertNull(suggest(history)("USD", "EUR", 100.0, date), "neither end is the base")
        assertNull(suggest(emptyList())("BRL", "USD", 100.0, date), "no rate known by that date")
        assertNull(suggest(history)("BRL", "USD", 0.0, date), "nothing typed yet")
    }

    @Test
    fun `a suggestion is rounded to what a money field can hold`() = runTest {
        val history = listOf(ExchangeRate("USD", date, 3.0, ExchangeRate.Source.USER))

        // 10 / 3 in a field that holds cents. Seeding more precision than it can hold would
        // make the first keystroke rewrite the number the user was shown.
        assertEquals(3.33, suggest(history)("BRL", "USD", 10.0, date)?.amount)
    }

    private fun collect(rates: RecordingRates) =
        CollectOperationRateUseCase(rates, Base("BRL"))

    private fun suggest(history: List<ExchangeRate>) =
        SuggestConvertedAmountUseCase(HistoryRates(history), Base("BRL"))

    private class Base(private val currency: String) : IBaseCurrencyRepository {
        override fun observe(): StateFlow<String> = MutableStateFlow(currency)
        override suspend fun set(currency: String) = throw NotImplementedError()
    }

    private class RecordingRates : IExchangeRateRepository {
        val recorded = mutableListOf<ExchangeRate>()

        override suspend fun record(rate: ExchangeRate) {
            recorded += rate
        }

        override suspend fun rateOn(currency: String, date: LocalDate): ExchangeRate? = null
        override fun observeAll(): Flow<List<ExchangeRate>> = flowOf(emptyList())
        override suspend fun getAll(): List<ExchangeRate> = emptyList()
        override suspend fun remove(rate: ExchangeRate) = throw NotImplementedError()
    }

    /** A history, resolved by the same policy the real repository's query applies. */
    private class HistoryRates(private val history: List<ExchangeRate>) : IExchangeRateRepository {
        override suspend fun rateOn(currency: String, date: LocalDate): ExchangeRate? = history
            .filter { it.currency == currency && it.date <= date }
            .maxByOrNull { it.date }

        override fun observeAll(): Flow<List<ExchangeRate>> = flowOf(history)
        override suspend fun getAll(): List<ExchangeRate> = history
        override suspend fun record(rate: ExchangeRate) = throw NotImplementedError()
        override suspend fun remove(rate: ExchangeRate) = throw NotImplementedError()
    }
}
