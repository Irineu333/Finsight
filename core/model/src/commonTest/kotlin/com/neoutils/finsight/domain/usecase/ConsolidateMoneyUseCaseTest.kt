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
        assertEquals(0.0, figure.terms.single().value)
        assertFalse(figure.isApproximate)
    }

    @Test
    fun `one currency that is the base is itself, exact`() = runTest {
        val figure = reducer()(MoneyByCurrency.of("BRL", 100.0), march, DisplayAmount::natural)

        assertEquals("BRL", figure.base?.currency)
        assertEquals(100.0, figure.terms.single().value)
        assertFalse(figure.isApproximate)
    }

    @Test
    fun `one currency that is not the base is itself, exact, with no rate`() = runTest {
        val figure = reducer()(MoneyByCurrency.of("USD", 50.0), march, DisplayAmount::natural)

        assertEquals(1, figure.terms.size)
        assertEquals("USD", figure.terms.single().currency)
        assertEquals(50.0, figure.terms.single().value)
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
        assertEquals(50.0, figure.terms.single().value)
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
        assertEquals(375.0, figure.terms.single().value, "100 + 50 × 5.50")
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
        assertEquals(100.0, figure.terms[0].value)
        assertEquals(50.0, figure.terms[1].value)
        assertTrue(figure.isApproximate)
        assertEquals(0, figure.baseIndex, "the base term is first, so a narrow surface degrades to it")
    }

    // --- the cases the table exists for ---

    @Test
    fun `removing the only rate returns the figure to its own term`() = runTest {
        val money = MoneyByCurrency.of(mapOf("BRL" to 100.0, "USD" to 50.0))

        val withRate = reducer(rates = arrayOf("USD" to 5.5))(money, march, DisplayAmount::natural)
        val without = reducer()(money, march, DisplayAmount::natural)

        assertEquals(listOf("BRL"), withRate.terms.map { it.currency })
        assertEquals(375.0, withRate.terms.single().value)
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
        assertEquals(375.0, figure.terms[0].value)
        assertEquals(10.0, figure.terms[1].value, "not turned into 1, not dropped, not zeroed")
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
            MoneyByCurrency.of(mapOf("BRL" to 10.0, "USD" to 33.33)),
            march,
            DisplayAmount::natural,
        )

        // 33.33 × 5.4321 = 181.0518... — the reducer is where that becomes money, and
        // it becomes it once: 10 + 181.05, never 191.0518 rounded later by a surface.
        assertEquals(191.05, figure.terms.single().value)
    }

    /**
     * **A currency the user holds nothing in is not a share of the figure.**
     *
     * Opening a second account and spending it back to zero is not an event that should
     * mark every total in the app. Carried in, `{BRL: 1000, USD: 0}` would read
     * `R$ 1.000,00 + US$ 0,00 ≈` on the dashboard — the mark over a number nothing was
     * converted for, and forever, since no rate ever removes a term.
     */
    @Test
    fun `a currency sitting at zero does not make a figure approximate`() = runTest {
        val figure = reducer()(
            MoneyByCurrency.of(mapOf("BRL" to 1_000.0, "USD" to 0.0)),
            march,
            DisplayAmount::natural,
        )

        assertEquals(listOf("BRL"), figure.terms.map { it.currency })
        assertEquals(1_000.0, figure.terms.single().value)
        assertFalse(figure.isApproximate, "nothing was converted, so nothing is approximate")
    }

    /**
     * And a figure that is nothing *but* zero keeps its own denomination: the rule drops
     * zeros only where another currency survives. A dollar account with no movement
     * reads `US$ 0,00`, not the base's zero.
     */
    @Test
    fun `an empty figure in one currency is still denominated by it`() = runTest {
        val figure = reducer()(MoneyByCurrency.of("USD", 0.0), march, DisplayAmount::natural)

        assertEquals("USD", figure.terms.single().currency)
        assertFalse(figure.isApproximate)
    }

    @Test
    fun `exactness travels inside every term, not only on the figure`() = runTest {
        // The mark cannot be lost on the way to a surface, so it rides in the same type
        // as the value — and the reducer, not the screen, is what puts it there.
        val exact = reducer()(MoneyByCurrency.of("USD", 50.0), march, DisplayAmount::natural)
        val approximate = reducer(rates = arrayOf("USD" to 5.5))(
            MoneyByCurrency.of(mapOf("BRL" to 100.0, "USD" to 50.0)),
            march,
            DisplayAmount::natural,
        )

        assertTrue(exact.terms.none { it.isApproximate })
        assertTrue(approximate.terms.all { it.isApproximate })
    }

    /**
     * **A term is marked when a rate passed through it, and not because the figure it
     * belongs to is approximate.** The figure holds currencies that do not add up — that
     * is what the badge explains — but `US$ 50,00` standing on its own is the ledger's
     * own answer, exact, and marking it would claim uncertainty about a number the app
     * knows perfectly well.
     */
    @Test
    fun `only the term a rate passed through is marked`() = runTest {
        val figure = reducer(rates = arrayOf("USD" to 5.5))(
            MoneyByCurrency.of(mapOf("BRL" to 100.0, "USD" to 50.0, "EUR" to 10.0)),
            march,
            DisplayAmount::natural,
        )

        assertEquals(listOf("BRL", "EUR"), figure.terms.map { it.currency })
        assertTrue(figure.terms[0].isApproximate, "the base term is what the rate reached")
        assertFalse(figure.terms[1].isApproximate, "no rate reached the euro; it is exact")
        assertTrue(figure.isApproximate, "the figure still does not add up to one number")
    }

    /**
     * And with no rate at all, nothing was converted: every term stands on its own, so no
     * line carries a mark — even though the figure is still not one number.
     */
    @Test
    fun `a figure no rate reached marks nothing`() = runTest {
        val figure = reducer()(
            MoneyByCurrency.of(mapOf("BRL" to 100.0, "USD" to 50.0)),
            march,
            DisplayAmount::natural,
        )

        assertEquals(listOf("BRL", "USD"), figure.terms.map { it.currency })
        assertTrue(figure.terms.none { it.isApproximate }, "a number nothing converted was marked")
        assertTrue(figure.isApproximate)
    }

    /**
     * The reference date is a fact about a **conversion**, so a figure no rate touched has
     * none — even though it is approximate.
     *
     * The two are different questions, and answering this one with `isApproximate` is what
     * let a surface tell a user with no rates at all that "what could be converted used the
     * rate of 10 March": naming a rate that was never applied and does not exist.
     */
    @Test
    fun `a figure no rate reached reports no date`() = runTest {
        val untouched = reducer()(
            MoneyByCurrency.of(mapOf("BRL" to 100.0, "USD" to 50.0)),
            march,
            DisplayAmount::natural,
        )

        assertNull(untouched.asOf, "no rate took part, so there is no date to report")
        assertEquals(0, untouched.baseIndex, "and a base term exists all the same")
        assertFalse(
            untouched.base!!.isApproximate,
            "money already in the base was converted by nothing — this is what a surface must read",
        )

        val converted = reducer(rates = arrayOf("USD" to 5.5))(
            MoneyByCurrency.of(mapOf("BRL" to 100.0, "USD" to 50.0)),
            march,
            DisplayAmount::natural,
        )

        assertEquals(march, converted.asOf)
        assertTrue(converted.base!!.isApproximate)
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
            figure.terms.map { it.policy },
        )
        assertEquals(100.0, figure.terms[0].value, "magnitude applies after conversion")
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
