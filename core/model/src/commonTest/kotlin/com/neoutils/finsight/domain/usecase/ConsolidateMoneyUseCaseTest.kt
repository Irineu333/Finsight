package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.ExchangeRate
import com.neoutils.finsight.domain.model.MoneyByCurrency
import com.neoutils.finsight.domain.repository.IBaseCurrencyRepository
import com.neoutils.finsight.domain.repository.IExchangeRateRepository
import com.neoutils.finsight.extension.DisplayAmount
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The five rows of design D9's table, plus the two cases the table exists to get right.
 *
 * The one worth naming: a user with every account in dollars and a Brazilian device.
 * His base is the real and he does not hold a cent of it — and he must see dollars,
 * exact and unmarked, **even with a dollar rate registered**. An earlier draft converted
 * his net worth the instant such a rate existed, and the rates screen listed exactly
 * that currency, inviting him to trip the problem himself.
 */
class ConsolidateMoneyUseCaseTest {

    private val march = LocalDate(2026, 3, 10)

    private fun reducer(
        base: String = "BRL",
        vararg rates: Pair<String, Double>,
    ) = ConsolidateMoneyUseCase(
        baseCurrencyRepository = FakeBaseCurrency(base),
        exchangeRateRepository = FakeRates(rates.toMap()),
    )

    // --- the table ---

    @Test
    fun `an empty figure is zero, exact, in the base`() = runTest {
        val figure = reducer()(MoneyByCurrency.zero, march, DisplayAmount::natural)

        assertEquals(1, figure.terms.size)
        assertEquals("BRL", figure.terms.single().currency)
        assertEquals(0.0, figure.terms.single().amount.value)
        assertFalse(figure.isApproximate)
    }

    @Test
    fun `one currency that is the base is itself, exact`() = runTest {
        val figure = reducer()(MoneyByCurrency.of("BRL", 100.0), march, DisplayAmount::natural)

        assertEquals("BRL", figure.base?.currency)
        assertEquals(100.0, figure.terms.single().amount.value)
        assertFalse(figure.isApproximate)
    }

    @Test
    fun `one currency that is not the base is itself, exact, with no rate`() = runTest {
        val figure = reducer()(MoneyByCurrency.of("USD", 50.0), march, DisplayAmount::natural)

        assertEquals(1, figure.terms.size)
        assertEquals("USD", figure.terms.single().currency)
        assertEquals(50.0, figure.terms.single().amount.value)
        assertFalse(figure.isApproximate)
        assertNull(figure.base, "the base did not take part; there was nothing to reconcile")
    }

    @Test
    fun `one currency that is not the base stays itself even with a rate registered`() = runTest {
        // The whole point of the first half of D9: converting here would trade an exact
        // number for an approximate one and get nothing back.
        val figure = reducer(rates = arrayOf("USD" to 5.5))(
            MoneyByCurrency.of("USD", 50.0),
            march,
            DisplayAmount::natural,
        )

        assertEquals(listOf("USD"), figure.terms.map { it.currency })
        assertEquals(50.0, figure.terms.single().amount.value)
        assertFalse(figure.isApproximate)
    }

    @Test
    fun `two currencies with a known rate reduce to one approximate term`() = runTest {
        val figure = reducer(rates = arrayOf("USD" to 5.5))(
            MoneyByCurrency.of(mapOf("BRL" to 100.0, "USD" to 50.0)),
            march,
            DisplayAmount::natural,
        )

        assertEquals(1, figure.terms.size)
        assertEquals("BRL", figure.terms.single().currency)
        assertEquals(375.0, figure.terms.single().amount.value, "100 + 50 × 5.50")
        assertTrue(figure.isApproximate, "a conversion happened, so the figure is not exact")
        assertEquals(0, figure.baseIndex)
    }

    @Test
    fun `two currencies without a rate keep both terms`() = runTest {
        val figure = reducer()(
            MoneyByCurrency.of(mapOf("BRL" to 100.0, "USD" to 50.0)),
            march,
            DisplayAmount::natural,
        )

        // Nothing invented and nothing omitted: the missing rate becomes one more term.
        assertEquals(listOf("BRL", "USD"), figure.terms.map { it.currency })
        assertEquals(100.0, figure.terms[0].amount.value)
        assertEquals(50.0, figure.terms[1].amount.value)
        assertTrue(figure.isApproximate)
        assertEquals(0, figure.baseIndex, "the base term is first, so a narrow surface degrades to it")
    }

    // --- the cases the table exists for ---

    @Test
    fun `removing the only rate returns the figure to its own term`() = runTest {
        val money = MoneyByCurrency.of(mapOf("BRL" to 0.0, "USD" to 50.0))

        val withRate = reducer(rates = arrayOf("USD" to 5.5))(money, march, DisplayAmount::natural)
        val without = reducer()(money, march, DisplayAmount::natural)

        assertEquals(listOf("BRL"), withRate.terms.map { it.currency })
        assertEquals(listOf("BRL", "USD"), without.terms.map { it.currency })
    }

    @Test
    fun `a currency with no rate survives beside the ones that converted`() = runTest {
        val figure = reducer(rates = arrayOf("USD" to 5.5))(
            MoneyByCurrency.of(mapOf("BRL" to 100.0, "USD" to 50.0, "EUR" to 10.0)),
            march,
            DisplayAmount::natural,
        )

        assertEquals(listOf("BRL", "EUR"), figure.terms.map { it.currency })
        assertEquals(375.0, figure.terms[0].amount.value)
        assertEquals(10.0, figure.terms[1].amount.value, "not turned into 1, not dropped, not zeroed")
    }

    @Test
    fun `no term converts when the base itself is absent and no rate is known`() = runTest {
        val figure = reducer()(
            MoneyByCurrency.of(mapOf("USD" to 50.0, "EUR" to 10.0)),
            march,
            DisplayAmount::natural,
        )

        assertEquals(listOf("EUR", "USD"), figure.terms.map { it.currency })
        assertNull(figure.baseIndex)
        assertTrue(figure.isApproximate)
    }

    @Test
    fun `conversion is rounded to cents, once, here`() = runTest {
        val figure = reducer(rates = arrayOf("USD" to 5.4321))(
            MoneyByCurrency.of(mapOf("BRL" to 0.0, "USD" to 33.33)),
            march,
            DisplayAmount::natural,
        )

        // 33.33 × 5.4321 = 181.0518... — the reducer is where that becomes money.
        assertEquals(181.05, figure.terms.single().amount.value)
    }

    @Test
    fun `the caller's sign policy travels into every term`() = runTest {
        val figure = reducer()(
            MoneyByCurrency.of(mapOf("BRL" to -100.0, "USD" to -50.0)),
            march,
            DisplayAmount::magnitude,
        )

        assertEquals(
            listOf(DisplayAmount.SignPolicy.MAGNITUDE, DisplayAmount.SignPolicy.MAGNITUDE),
            figure.terms.map { it.amount.policy },
        )
        assertEquals(100.0, figure.terms[0].amount.value, "magnitude applies after conversion")
    }
}

internal class FakeBaseCurrency(base: String) : IBaseCurrencyRepository {
    private val flow = MutableStateFlow(base)
    override fun observe(): StateFlow<String> = flow
    override suspend fun set(currency: String) { flow.value = currency }
}

internal class FakeRates(
    private val rates: Map<String, Double> = emptyMap(),
) : IExchangeRateRepository {

    val saved = mutableListOf<ExchangeRate>()

    private fun rateOf(currency: String, date: LocalDate) = rates[currency]?.let {
        ExchangeRate(currency = currency, date = date, rate = it, source = ExchangeRate.Source.DERIVED)
    }

    override suspend fun rateAsOf(currency: String, date: LocalDate) = rateOf(currency, date)

    override suspend fun ratesAsOf(date: LocalDate): Map<String, ExchangeRate> =
        rates.keys.associateWith { rateOf(it, date)!! }

    override fun observeAll(): Flow<List<ExchangeRate>> = flowOf(emptyList())

    override suspend fun save(rate: ExchangeRate) { saved += rate }

    override suspend fun remove(rate: ExchangeRate) { saved.remove(rate) }
}
