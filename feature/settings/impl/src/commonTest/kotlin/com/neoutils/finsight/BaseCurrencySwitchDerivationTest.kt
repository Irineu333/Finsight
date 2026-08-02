package com.neoutils.finsight

import com.neoutils.finsight.database.repository.resolveRate
import com.neoutils.finsight.domain.model.ExchangeRate
import kotlinx.datetime.LocalDate
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **Switching the base currency preserves the archive.**
 *
 * The arithmetic used to be written out here, because shipping it would have been
 * shipping a feature that was not offered. It is shipped now, so this exercises the
 * **real resolver**: the inverse and the triangulation are the ones that run on a
 * device, with the same numbers.
 *
 * What the suite claims has not changed, and it is the property the whole design rests
 * on: re-expressing the archive against a different base is *reading*. The old base
 * against the new one is the **inverse** of a rate that exists; every other currency
 * re-expresses by **triangulation** over rates of the same date; and nothing is written
 * along the way.
 */
class BaseCurrencySwitchDerivationTest {

    private val date = LocalDate(2026, 3, 14)

    /** Base BRL: one dollar is 5.50 reais, one euro is 6.00 reais. */
    private val archive = listOf(
        rate("USD", 5.50),
        rate("EUR", 6.00),
    )

    @Test
    fun `the old base against the new one is the inverse of a rate already stored`() {
        // 1 BRL ≈ 0.1818 USD — nothing was fetched, nothing was written.
        assertClose(0.181818, archive.resolveRate(from = "BRL", to = "USD"))
    }

    @Test
    fun `every other currency re-expresses by triangulation over rates of the same date`() {
        // EUR against the new base USD, over the real: (BRL per EUR) / (BRL per USD).
        assertClose(1.090909, archive.resolveRate(from = "EUR", to = "USD"))
    }

    @Test
    fun `the derivation is a round trip - re-expressing twice returns the original`() {
        val usdPerEur = archive.resolveRate(from = "EUR", to = "USD")!!
        val brlPerUsd = archive.resolveRate(from = "USD", to = "BRL")!!

        // Back to a BRL base: (USD per EUR) × (BRL per USD).
        assertClose(6.00, usdPerEur * brlPerUsd)
    }

    /**
     * The assertion that gained weight rather than losing it: it is now made against a
     * **real** switch of base, which is exactly the implementation it exists to bar —
     * the one that would rewrite the archive, and would be a migration.
     */
    @Test
    fun `no stored row changes`() {
        val before = archive.map { it.copy() }

        archive.resolveRate(from = "EUR", to = "USD")
        archive.resolveRate(from = "BRL", to = "USD")

        assertEquals(before, archive)
        assertTrue(archive.all { it.source == ExchangeRate.Source.USER })
    }

    private fun rate(currency: String, value: Double) = ExchangeRate(
        id = 0,
        currency = currency,
        counterCurrency = "BRL",
        date = date,
        rate = value,
        source = ExchangeRate.Source.USER,
    )

    private fun assertClose(expected: Double, actual: Double?) {
        assertTrue(
            actual != null && abs(expected - actual) < 1e-6,
            "expected ~$expected but was $actual",
        )
    }
}
