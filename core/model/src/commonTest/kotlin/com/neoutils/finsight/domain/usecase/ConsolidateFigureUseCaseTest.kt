package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.CurrencyBalance
import com.neoutils.finsight.domain.model.ExchangeRate
import com.neoutils.finsight.domain.repository.IExchangeRateRepository
import com.neoutils.finsight.extension.DisplayAmount.SignPolicy
import com.neoutils.finsight.extension.explanationIsOwed
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The five rows of the consolidation table, and the one they exist for.
 *
 * | per-currency result | figure |
 * |---|---|
 * | empty | zero — exact |
 * | `{BRL: 100}`, base BRL | `R$ 100,00` — exact |
 * | `{USD: 50}`, base BRL, **with or without** a rate | `US$ 50,00` — **exact** |
 * | `{BRL: 100, USD: 50}`, rate known | one term in BRL — approximate |
 * | `{BRL: 100, USD: 50}`, **no** USD rate | two terms — approximate |
 *
 * The third row is the one the rule exists for: a single currency is not reconciled with
 * anything, so converting it would trade an exact figure for an approximate one and buy
 * nothing. It is what lets a user holding only dollars, on a device whose locale resolves
 * reais, read the whole app exactly — with the base currency appearing nowhere.
 */
class ConsolidateFigureUseCaseTest {

    private val date = LocalDate.parse("2026-05-10")

    @Test
    fun `nothing at all is zero, exact, in the base`() = runTest {
        val figure = consolidate(CurrencyBalance.zero)

        assertEquals(1, figure.terms.size)
        assertEquals(0.0, figure.primary.value)
        assertEquals("BRL", figure.primary.currency)
        assertFalse(figure.isApproximate)
    }

    @Test
    fun `a result in the base alone is the ledger's own number, exact`() = runTest {
        val figure = consolidate(CurrencyBalance.of("BRL", 100.0))

        assertEquals(100.0, figure.primary.value)
        assertEquals("BRL", figure.primary.currency)
        assertFalse(figure.isApproximate)
    }

    @Test
    fun `a single foreign currency stays in its own currency, exact, when no rate exists`() = runTest {
        val figure = consolidate(CurrencyBalance.of("USD", 50.0))

        assertEquals(50.0, figure.primary.value)
        assertEquals("USD", figure.primary.currency)
        assertFalse(figure.isApproximate)
    }

    @Test
    fun `a single foreign currency stays in its own currency even with a rate on file`() = runTest {
        // The case the earlier design got wrong: it converted this the moment a dollar rate
        // existed, and the rates screen listed exactly that currency, inviting the user to
        // trigger it.
        val figure = consolidate(CurrencyBalance.of("USD", 50.0), rates = mapOf("USD" to 5.5))

        assertEquals(50.0, figure.primary.value)
        assertEquals("USD", figure.primary.currency)
        assertFalse(figure.isApproximate, "nothing was reconciled, so nothing is approximate")
    }

    @Test
    fun `two currencies with a known rate become one approximate term in the base`() = runTest {
        val figure = consolidate(
            CurrencyBalance.of(mapOf("BRL" to 100.0, "USD" to 50.0)),
            rates = mapOf("USD" to 5.5),
        )

        assertEquals(1, figure.terms.size)
        assertEquals(375.0, figure.primary.value)
        assertEquals("BRL", figure.primary.currency)
        assertTrue(figure.isApproximate)
    }

    @Test
    fun `two currencies without a rate keep both shares, and invent nothing`() = runTest {
        val figure = consolidate(CurrencyBalance.of(mapOf("BRL" to 100.0, "USD" to 50.0)))

        assertEquals(2, figure.terms.size)
        // The base term first: it is the one a surface with room for a single line keeps.
        assertEquals(100.0, figure.terms[0].value)
        assertEquals("BRL", figure.terms[0].currency)
        assertEquals(50.0, figure.terms[1].value)
        assertEquals("USD", figure.terms[1].currency)
        // A missing rate is neither `1` nor an omission: the dollars are still there, in
        // dollars, and the figure as a whole is approximate because it does not add up to one
        // number.
        assertTrue(figure.isApproximate)
        assertFalse(figure.terms[1].isApproximate, "the unconverted share was not converted")
    }

    @Test
    fun `three currencies reduce as far as the rates reach and no further`() = runTest {
        val figure = consolidate(
            CurrencyBalance.of(mapOf("BRL" to 100.0, "USD" to 50.0, "EUR" to 10.0)),
            rates = mapOf("USD" to 5.5),
        )

        assertEquals(2, figure.terms.size)
        assertEquals(375.0, figure.terms[0].value, "the reais and the dollars the rate reached")
        assertEquals("EUR", figure.terms[1].currency)
        assertEquals(10.0, figure.terms[1].value)
    }

    @Test
    fun `the rate used is the one in force on the figure's own date`() = runTest {
        val history = listOf(
            ExchangeRate("USD", LocalDate.parse("2026-01-01"), 5.0, ExchangeRate.Source.USER),
            ExchangeRate("USD", LocalDate.parse("2026-09-01"), 6.0, ExchangeRate.Source.USER),
        )
        val useCase = ConsolidateFigureUseCase(HistoryRates(history))
        // A real reais share, not a zero one: a currency contributing nothing is not a term
        // at all, and the figure would be single-currency USD with no conversion to date.
        val balance = CurrencyBalance.of(mapOf("BRL" to 1.0, "USD" to 10.0))

        // May is governed by January's rate, not by September's — a figure of a closed period
        // does not move when a later rate is entered.
        assertEquals(51.0, useCase(balance, base = "BRL", date = LocalDate.parse("2026-05-10")).comparable)
        assertEquals(61.0, useCase(balance, base = "BRL", date = LocalDate.parse("2026-10-10")).comparable)
    }

    @Test
    fun `the caller's sign policy is what each term reads by`() = runTest {
        val figure = consolidate(
            CurrencyBalance.of(mapOf("BRL" to 100.0, "USD" to 50.0)),
            policy = SignPolicy.FORCED_NEGATIVE,
        )

        assertTrue(figure.terms.all { it.policy == SignPolicy.FORCED_NEGATIVE })
        assertTrue(figure.terms.all { it.value <= 0.0 }, "spending subtracts in every term of it")
    }

    @Test
    fun `every figure comes back with its exactness, because there is no other way to get one`() = runTest {
        // Not an assertion about a value: the type has no constructor that omits the
        // denomination, and the denomination has no state without exactness. This test
        // exists so that removing either would break something.
        val figure = consolidate(CurrencyBalance.of("BRL", 10.0))

        assertEquals(figure.terms.map { it.denomination.isApproximate }, figure.terms.map { false })
    }

    @Test
    fun `a converted figure carries the quotes that produced it`() = runTest {
        val history = listOf(
            ExchangeRate("USD", LocalDate.parse("2026-01-01"), 5.0, ExchangeRate.Source.USER),
            ExchangeRate("EUR", LocalDate.parse("2026-02-01"), 6.0, ExchangeRate.Source.OPERATION),
        )
        val figure = ConsolidateFigureUseCase(HistoryRates(history))(
            CurrencyBalance.of(mapOf("BRL" to 100.0, "USD" to 10.0, "EUR" to 10.0)),
            base = "BRL",
            date = date,
        ).figure

        // The date is the quote's own — January's, months before the figure — because that
        // is the one the reduction used, and explaining it with today's would be a second
        // answer to a question already answered.
        assertEquals(
            listOf("EUR" to LocalDate.parse("2026-02-01"), "USD" to LocalDate.parse("2026-01-01")),
            figure.appliedRates.map { it.currency to it.date },
        )
        assertEquals(listOf(6.0, 5.0), figure.appliedRates.map { it.rate })
        assertTrue(figure.appliedRates.all { it.baseCurrency == "BRL" })
    }

    @Test
    fun `the base's own share is not a rate to reveal`() = runTest {
        val figure = consolidate(
            CurrencyBalance.of(mapOf("BRL" to 100.0, "USD" to 50.0)),
            rates = mapOf("USD" to 5.5),
        )

        // It is worth one of itself by definition, and a footer reading "1 BRL = R$ 1,00"
        // explains nothing.
        assertEquals(listOf("USD"), figure.appliedRates.map { it.currency })
    }

    @Test
    fun `an exact figure carries no rate at all`() = runTest {
        assertTrue(consolidate(CurrencyBalance.of("USD", 50.0), rates = mapOf("USD" to 5.5)).appliedRates.isEmpty())
        assertTrue(consolidate(CurrencyBalance.of("BRL", 10.0)).appliedRates.isEmpty())
        assertTrue(consolidate(CurrencyBalance.zero).appliedRates.isEmpty())
    }

    @Test
    fun `terms no rate reached leave the figure with nothing to reveal`() = runTest {
        val figure = consolidate(CurrencyBalance.of(mapOf("BRL" to 100.0, "USD" to 50.0)))

        // Two terms, both exact, and the figure still owes an explanation — of the elision
        // rather than of a conversion. There is simply no rate to name in it.
        assertTrue(figure.appliedRates.isEmpty())
        assertTrue(explanationIsOwed(listOf(figure)))
    }

    @Test
    fun `the number a caller ranks by is what the rates reached, and it says when that is less than the whole`() = runTest {
        // One currency: the figure and the number are the same thing, and nothing is left out.
        val whole = consolidated(CurrencyBalance.of("USD", 50.0))
        assertEquals(50.0, whole.comparable)
        assertFalse(whole.isPartial)

        // Two currencies, one rate: the number is the reduced total, and it is the whole of
        // the figure — nothing escaped the rates.
        val converted = consolidated(
            CurrencyBalance.of(mapOf("BRL" to 100.0, "USD" to 50.0)),
            rates = mapOf("USD" to 5.5),
        )
        assertEquals(375.0, converted.comparable)
        assertFalse(converted.isPartial)
        assertTrue(converted.isApproximate, "a conversion took part")

        // Two currencies, no rate: the number is only the base's share, and `isPartial` is
        // what carries that into any fraction computed over it.
        val partial = consolidated(CurrencyBalance.of(mapOf("BRL" to 100.0, "USD" to 50.0)))
        assertEquals(100.0, partial.comparable)
        assertTrue(partial.isPartial)
        assertTrue(partial.isApproximate)
    }

    @Test
    fun `a figure no rate reached at all ranks as nothing, and says so`() = runTest {
        val figure = consolidated(CurrencyBalance.of(mapOf("USD" to 50.0, "EUR" to 10.0)))

        // There is no base term to rank by. Answering `50` — the first term, in dollars —
        // would compare dollars against reais elsewhere in the same list.
        assertEquals(0.0, figure.comparable)
        assertTrue(figure.isPartial)
    }

    @Test
    fun `a currency that contributes nothing is not a term`() = runTest {
        // A grouped read answers with a row per currency it *touched*: a perimeter whose
        // dollar legs cancel comes back with `USD: 0` beside the reais. Splitting that into
        // two terms would mark an exact figure as approximate over nothing.
        val figure = consolidated(CurrencyBalance.of(mapOf("BRL" to 100.0, "USD" to 0.0)))

        assertEquals(100.0, figure.comparable)
        assertFalse(figure.isPartial)
        assertFalse(figure.figure.isApproximate)
        assertTrue(figure.figure.isSingleTerm)
    }

    @Test
    fun `a figure of nothing at all keeps its own denomination`() = runTest {
        // Not the base: the ledger answered in dollars, and it answered zero. Falling back to
        // the base here would show `R$ 0,00` over a dollar perimeter.
        val figure = consolidated(CurrencyBalance.of("USD", 0.0))

        assertEquals(0.0, figure.comparable)
        assertEquals(listOf("USD"), figure.figure.terms.map { it.currency })
    }

    private suspend fun consolidate(
        balance: CurrencyBalance,
        rates: Map<String, Double> = emptyMap(),
        policy: SignPolicy = SignPolicy.NATURAL,
    ) = ConsolidateFigureUseCase(FlatRates(rates))(balance, base = "BRL", date = date, policy = policy).figure

    /** The whole result, for the cases that assert about the number a caller ranks by. */
    private suspend fun consolidated(
        balance: CurrencyBalance,
        rates: Map<String, Double> = emptyMap(),
    ) = ConsolidateFigureUseCase(FlatRates(rates))(balance, base = "BRL", date = date)

    /** One rate per currency, in force forever — the shape most of these cases need. */
    private class FlatRates(private val rates: Map<String, Double>) : IExchangeRateRepository {
        override suspend fun rateOn(currency: String, date: LocalDate): ExchangeRate? =
            rates[currency]?.let { ExchangeRate(currency, date, it, ExchangeRate.Source.USER) }

        override fun observeAll(): Flow<List<ExchangeRate>> = flowOf(emptyList())
        override suspend fun getAll(): List<ExchangeRate> = emptyList()
        override suspend fun record(rate: ExchangeRate) = throw NotImplementedError()
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
