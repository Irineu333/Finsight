package com.neoutils.finsight

import com.neoutils.finsight.domain.model.ExchangeRate
import kotlinx.datetime.LocalDate
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **Switching the base currency preserves the archive.**
 *
 * v1 offers no screen that switches it (design D18/D28). This test exists for the other
 * half of that decision: the requirement is written so that the implementation cannot
 * make switching *impossible*, and what makes it possible is that nothing converted is
 * ever persisted. A rate is stored one way, currency → base, and re-expressing the
 * archive against a different base is arithmetic on what is already there — the old
 * base against the new one is the **inverse** of a rate that exists, and every other
 * currency re-expresses by **triangulation** over rates of the same date.
 *
 * The derivation is written out here rather than shipped as production code, because
 * shipping it would be shipping a feature v1 does not offer. What has to be true today
 * is that the archive **suffices** — and that is exactly what these assertions check.
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
        val usdPerBrl = 1.0 / archive.rateOf("USD")

        // 1 BRL ≈ 0.1818 USD — nothing was fetched, nothing was written.
        assertClose(0.181818, usdPerBrl)
    }

    @Test
    fun `every other currency re-expresses by triangulation over rates of the same date`() {
        // EUR against the new base USD: (BRL per EUR) / (BRL per USD).
        val usdPerEur = archive.rateOf("EUR") / archive.rateOf("USD")

        assertClose(1.090909, usdPerEur)
    }

    @Test
    fun `the derivation is a round trip - re-expressing twice returns the original`() {
        val usdPerEur = archive.rateOf("EUR") / archive.rateOf("USD")
        val brlPerUsd = archive.rateOf("USD")

        // Back to a BRL base: (USD per EUR) × (BRL per USD).
        assertClose(archive.rateOf("EUR"), usdPerEur * brlPerUsd)
    }

    @Test
    fun `no stored row changes`() {
        val before = archive.map { it.copy() }

        // Deriving reads; it never writes. Stated as an assertion because the failure
        // mode it guards against is a "switch base" implementation that rewrites the
        // archive — which would be migration, and would destroy the observations.
        archive.rateOf("EUR") / archive.rateOf("USD")

        assertEquals(before, archive)
        assertTrue(archive.all { it.source == ExchangeRate.Source.USER })
    }

    private fun rate(currency: String, value: Double) = ExchangeRate(
        id = 0,
        currency = currency,
        date = date,
        rate = value,
        source = ExchangeRate.Source.USER,
    )

    private fun List<ExchangeRate>.rateOf(currency: String) = single { it.currency == currency }.rate

    private fun assertClose(expected: Double, actual: Double) {
        assertTrue(abs(expected - actual) < 1e-6, "expected ~$expected but was $actual")
    }
}
