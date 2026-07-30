package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.CurrencyBalance
import com.neoutils.finsight.domain.model.ExchangeRate
import com.neoutils.finsight.domain.repository.IExchangeRateRepository
import com.neoutils.finsight.extension.DisplayAmount.SignPolicy
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
        val balance = CurrencyBalance.of(mapOf("BRL" to 0.0, "USD" to 10.0))

        // May is governed by January's rate, not by September's — a figure of a closed period
        // does not move when a later rate is entered.
        assertEquals(50.0, useCase(balance, base = "BRL", date = LocalDate.parse("2026-05-10")).primary.value)
        assertEquals(60.0, useCase(balance, base = "BRL", date = LocalDate.parse("2026-10-10")).primary.value)
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

    private suspend fun consolidate(
        balance: CurrencyBalance,
        rates: Map<String, Double> = emptyMap(),
        policy: SignPolicy = SignPolicy.NATURAL,
    ) = ConsolidateFigureUseCase(FlatRates(rates))(balance, base = "BRL", date = date, policy = policy)

    /** One rate per currency, in force forever — the shape most of these cases need. */
    private class FlatRates(private val rates: Map<String, Double>) : IExchangeRateRepository {
        override suspend fun rateOn(currency: String, date: LocalDate): ExchangeRate? =
            rates[currency]?.let { ExchangeRate(currency, date, it, ExchangeRate.Source.USER) }

        override fun observeAll(): Flow<List<ExchangeRate>> = flowOf(emptyList())
        override suspend fun getAll(): List<ExchangeRate> = emptyList()
        override suspend fun record(rate: ExchangeRate) = throw NotImplementedError()
    }

    /** A history, resolved by the same policy the real repository's query applies. */
    private class HistoryRates(private val history: List<ExchangeRate>) : IExchangeRateRepository {
        override suspend fun rateOn(currency: String, date: LocalDate): ExchangeRate? = history
            .filter { it.currency == currency && it.date <= date }
            .maxByOrNull { it.date }

        override fun observeAll(): Flow<List<ExchangeRate>> = flowOf(history)
        override suspend fun getAll(): List<ExchangeRate> = history
        override suspend fun record(rate: ExchangeRate) = throw NotImplementedError()
    }
}
