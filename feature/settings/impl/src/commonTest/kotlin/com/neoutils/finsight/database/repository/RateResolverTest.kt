package com.neoutils.finsight.database.repository

import com.neoutils.finsight.domain.model.ExchangeRate
import kotlinx.datetime.LocalDate
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The declared precedence: direct ▸ inverse ▸ one pivot, and never two hops. */
class RateResolverTest {

    private val march = LocalDate(2026, 3, 14)
    private val february = LocalDate(2026, 2, 1)

    private fun rate(
        from: String,
        to: String,
        value: Double,
        date: LocalDate = march,
    ) = ExchangeRate(
        currency = from,
        counterCurrency = to,
        date = date,
        rate = value,
        source = ExchangeRate.Source.USER,
    )

    private fun assertClose(expected: Double, actual: Double?) {
        assertTrue(actual != null && abs(expected - actual) < 1e-9, "expected ~$expected but was $actual")
    }

    @Test
    fun `the direct observation beats the pivot`() {
        val archive = listOf(
            rate("EUR", "BRL", 6.0),
            rate("EUR", "USD", 1.1),
            rate("USD", "BRL", 5.5),
        )

        // The pivot over USD would answer 6.05; the direct one is what stands.
        assertClose(6.0, archive.resolveRate("EUR", "BRL"))
    }

    @Test
    fun `the inverse beats the pivot`() {
        val archive = listOf(
            rate("BRL", "EUR", 0.2),
            rate("EUR", "USD", 1.1),
            rate("USD", "BRL", 5.5),
        )

        assertClose(5.0, archive.resolveRate("EUR", "BRL"))
    }

    /**
     * The scenario the whole change exists for: the base becomes the dollar, the archive
     * holds only the euro and the dollar against the real, and the euro against the
     * dollar resolves over the real — with no row created or altered.
     */
    @Test
    fun `a triangulation resolves what the base switch left implicit`() {
        val archive = listOf(
            rate("USD", "BRL", 5.5),
            rate("EUR", "BRL", 6.0),
        )

        assertClose(6.0 / 5.5, archive.resolveRate("EUR", "USD"))
    }

    @Test
    fun `two hops are not composed`() {
        // EUR → BRL → USD → JPY would reach it; one hop does not.
        val archive = listOf(
            rate("EUR", "BRL", 6.0),
            rate("USD", "BRL", 5.5),
            rate("USD", "JPY", 150.0),
        )

        assertNull(archive.resolveRate("EUR", "JPY"))
    }

    @Test
    fun `two possible pivots always give the same answer`() {
        val archive = listOf(
            rate("EUR", "BRL", 6.0),
            rate("USD", "BRL", 5.5),
            rate("EUR", "GBP", 0.85),
            rate("USD", "GBP", 0.78),
        )

        val answer = archive.resolveRate("EUR", "USD")

        assertEquals(answer, archive.reversed().resolveRate("EUR", "USD"))
        assertEquals(answer, archive.shuffled(kotlin.random.Random(7)).resolveRate("EUR", "USD"))
        // BRL sorts before GBP, and both legs of both pivots are of the same date.
        assertClose(6.0 / 5.5, answer)
    }

    @Test
    fun `the pivot with the most recent legs wins`() {
        val archive = listOf(
            rate("EUR", "BRL", 6.0, february),
            rate("USD", "BRL", 5.5, february),
            rate("EUR", "GBP", 0.85, march),
            rate("USD", "GBP", 0.78, march),
        )

        assertClose(0.85 / 0.78, archive.resolveRate("EUR", "USD"))
    }

    /** An absent path is an absent rate, and an absent rate MUST NOT become `1`. */
    @Test
    fun `nothing resolves to null, and null is not one`() {
        val archive = listOf(rate("USD", "BRL", 5.5))

        assertNull(archive.resolveRate("JPY", "CHF"))
        assertNull(emptyList<ExchangeRate>().resolveRate("USD", "BRL"))
    }

    @Test
    fun `a currency against itself is one`() {
        assertClose(1.0, emptyList<ExchangeRate>().resolveRate("BRL", "BRL"))
    }
}
