package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.ExchangeRate
import com.neoutils.finsight.domain.repository.IExchangeRateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * What the archive implies the other end of a crossing is worth — and, just as much,
 * **which day it learned it**.
 *
 * The date is what tells a pre-filled field from an offered one: what the user types
 * into the second field becomes a harvested rate, so filling it in from an old quote
 * would write the old rate back as a new observation, in silence and in a loop.
 */
class SuggestCrossCurrencyAmountUseCaseTest {

    private val today = LocalDate(2026, 7, 20)
    private val twoWeeksAgo = LocalDate(2026, 7, 5)

    private class Archive(private val rate: ExchangeRate?) : IExchangeRateRepository {
        override suspend fun rateAsOf(currency: String, date: LocalDate) =
            rate?.takeIf { it.currency == currency && it.date <= date }

        override suspend fun ratesAsOf(date: LocalDate) = emptyMap<String, ExchangeRate>()
        override fun observeAll(): Flow<List<ExchangeRate>> = flowOf(emptyList())
        override suspend fun save(rate: ExchangeRate) = Unit
        override suspend fun remove(rate: ExchangeRate) = Unit
    }

    private fun suggester(
        base: String = "BRL",
        rate: ExchangeRate? = ExchangeRate(
            currency = "USD",
            date = today,
            rate = 5.5,
            source = ExchangeRate.Source.DERIVED,
        ),
    ) = SuggestCrossCurrencyAmountUseCase(FakeBaseCurrency(base), Archive(rate))

    @Test
    fun `from the base, the rate divides`() = runTest {
        val suggestion = suggester()(amount = 550.0, from = "BRL", to = "USD", on = today)

        assertEquals(100.0, suggestion?.amount)
        assertEquals(today, suggestion?.asOf)
    }

    @Test
    fun `to the base, the rate multiplies`() = runTest {
        val suggestion = suggester()(amount = 100.0, from = "USD", to = "BRL", on = today)

        assertEquals(550.0, suggestion?.amount)
    }

    /** The whole point of carrying a date: this one must not be pre-filled. */
    @Test
    fun `an older rate still answers, saying which day it is from`() = runTest {
        val suggestion = suggester(
            rate = ExchangeRate(
                currency = "USD",
                date = twoWeeksAgo,
                rate = 5.0,
                source = ExchangeRate.Source.USER,
            ),
        )(amount = 100.0, from = "USD", to = "BRL", on = today)

        assertEquals(500.0, suggestion?.amount)
        assertEquals(twoWeeksAgo, suggestion?.asOf)
    }

    @Test
    fun `between two non-base currencies it says nothing at all`() = runTest {
        // Triangulating two rates would be a guess wearing an observation's clothes —
        // the same reason harvesting refuses to learn from such an operation — and the
        // sentence the form would have to say ("by the rate of 05/07") cannot be said
        // when two rates carry two dates.
        assertNull(suggester()(amount = 100.0, from = "USD", to = "EUR", on = today))
    }

    @Test
    fun `with no rate, and with nothing stated, there is nothing to offer`() = runTest {
        assertNull(suggester(rate = null)(amount = 100.0, from = "USD", to = "BRL", on = today))
        assertNull(suggester()(amount = 0.0, from = "USD", to = "BRL", on = today))
        assertNull(suggester()(amount = 100.0, from = "USD", to = "USD", on = today))
    }

    @Test
    fun `the rate an operation applied is read back from its own two ends`() {
        assertEquals(5.5, impliedRate(sourceAmount = 100.0, targetAmount = 550.0))
        assertNull(impliedRate(sourceAmount = 0.0, targetAmount = 550.0))
        assertNull(impliedRate(sourceAmount = 100.0, targetAmount = 0.0))
    }
}
