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
        assertClose(6.0, archive.resolve("EUR", "BRL")?.rate)
    }

    @Test
    fun `the inverse beats the pivot`() {
        val archive = listOf(
            rate("BRL", "EUR", 0.2),
            rate("EUR", "USD", 1.1),
            rate("USD", "BRL", 5.5),
        )

        assertClose(5.0, archive.resolve("EUR", "BRL")?.rate)
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

        assertClose(6.0 / 5.5, archive.resolve("EUR", "USD")?.rate)
    }

    @Test
    fun `two hops are not composed`() {
        // EUR → BRL → USD → JPY would reach it; one hop does not.
        val archive = listOf(
            rate("EUR", "BRL", 6.0),
            rate("USD", "BRL", 5.5),
            rate("USD", "JPY", 150.0),
        )

        assertNull(archive.resolve("EUR", "JPY"))
    }

    @Test
    fun `two possible pivots always give the same answer`() {
        val archive = listOf(
            rate("EUR", "BRL", 6.0),
            rate("USD", "BRL", 5.5),
            rate("EUR", "GBP", 0.85),
            rate("USD", "GBP", 0.78),
        )

        val answer = archive.resolve("EUR", "USD")

        assertEquals(answer, archive.reversed().resolve("EUR", "USD"))
        assertEquals(answer, archive.shuffled(kotlin.random.Random(7)).resolve("EUR", "USD"))
        // BRL sorts before GBP, and both legs of both pivots are of the same date.
        assertClose(6.0 / 5.5, answer?.rate)
    }

    @Test
    fun `the pivot with the most recent legs wins`() {
        val archive = listOf(
            rate("EUR", "BRL", 6.0, february),
            rate("USD", "BRL", 5.5, february),
            rate("EUR", "GBP", 0.85, march),
            rate("USD", "GBP", 0.78, march),
        )

        assertClose(0.85 / 0.78, archive.resolve("EUR", "USD")?.rate)
    }

    /** An absent path is an absent rate, and an absent rate MUST NOT become `1`. */
    @Test
    fun `nothing resolves to null and null is not one`() {
        val archive = listOf(rate("USD", "BRL", 5.5))

        assertNull(archive.resolve("JPY", "CHF"))
        assertNull(emptyList<ExchangeRate>().resolve("USD", "BRL"))
    }

    @Test
    fun `a currency against itself is one`() {
        assertClose(1.0, emptyList<ExchangeRate>().resolve("BRL", "BRL")?.rate)
    }

    private fun rate(
        from: String,
        to: String,
        value: Double,
        source: ExchangeRate.Source,
        date: LocalDate = march,
    ) = rate(from, to, value, date).copy(source = source)

    /** The direct answer *is* the observation, so it declares that observation's origin. */
    @Test
    fun `the direct answer declares its own origin`() {
        val archive = listOf(rate("USD", "BRL", 5.5, ExchangeRate.Source.REMOTE))

        assertEquals(ExchangeRate.Source.REMOTE, archive.resolve("USD", "BRL")?.source)
    }

    /** The inverse is the **same** observation read backwards, so it keeps its origin. */
    @Test
    fun `the inverse keeps the origin of the observation it read`() {
        val archive = listOf(rate("BRL", "USD", 0.18, ExchangeRate.Source.REMOTE))

        val resolved = archive.resolve("USD", "BRL")

        assertClose(1.0 / 0.18, resolved?.rate)
        assertEquals(ExchangeRate.Source.REMOTE, resolved?.source)
    }

    @Test
    fun `the triangulation declares the weakest of its two legs`() {
        val userAndRemote = listOf(
            rate("EUR", "BRL", 6.0, ExchangeRate.Source.USER),
            rate("USD", "BRL", 5.0, ExchangeRate.Source.REMOTE),
        )
        assertEquals(ExchangeRate.Source.REMOTE, userAndRemote.resolve("EUR", "USD")?.source)

        val remoteAndDerived = listOf(
            rate("EUR", "BRL", 6.0, ExchangeRate.Source.REMOTE),
            rate("USD", "BRL", 5.0, ExchangeRate.Source.DERIVED),
        )
        assertEquals(ExchangeRate.Source.DERIVED, remoteAndDerived.resolve("EUR", "USD")?.source)
    }

    /**
     * The origins do not touch the path precedence: the inverse of a `USER` observation
     * still beats a pivot whose legs are `USER` too, and the number is the inverse's.
     */
    @Test
    fun `the origin does not reorder the paths`() {
        val archive = listOf(
            rate("BRL", "EUR", 0.2, ExchangeRate.Source.DERIVED),
            rate("EUR", "USD", 1.1, ExchangeRate.Source.USER),
            rate("USD", "BRL", 5.5, ExchangeRate.Source.USER),
        )

        val resolved = archive.resolve("EUR", "BRL")

        assertClose(5.0, resolved?.rate)
        assertEquals(ExchangeRate.Source.DERIVED, resolved?.source)
    }
}
