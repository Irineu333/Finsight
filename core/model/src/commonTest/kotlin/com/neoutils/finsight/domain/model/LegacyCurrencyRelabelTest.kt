package com.neoutils.finsight.domain.model

import com.neoutils.finsight.extension.DeviceRegion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Which devices the legacy relabelling fires on — and, more to the point, which it
 * does not.
 *
 * The resolution is where the false positive of design D30 is narrowed, so it is where
 * the narrowing has to be proved: only a statement about **location** fires it, a device
 * that cannot make one is left alone, and the curated catalog bars a currency the app
 * does not offer instead of coercing it into the currency of last resort.
 *
 * The region arrives as a value here rather than being read from a locale, which is the
 * point of the change this test was rewritten for: a locale's country is the country of
 * the chosen *language*, and on Android that is the only country there is.
 */
class LegacyCurrencyRelabelTest {

    private fun region(code: String?) = DeviceRegion { code }

    @Test
    fun `a foreign region relabels`() {
        assertEquals("USD", legacyRelabelCurrency(region("USD")))
        assertEquals("EUR", legacyRelabelCurrency(region("EUR")))
    }

    @Test
    fun `the region of origin is not touched`() {
        assertNull(legacyRelabelCurrency(region(LEGACY_DENOMINATION)))
    }

    /**
     * A device that cannot say where it is says nothing, and nothing is what happens.
     *
     * This is the whole of the narrowing: on Android the only country available without
     * a location signal is the one attached to the interface language, so a Brazilian
     * reading English would have had every row they own re-denominated in dollars,
     * irreversibly, for reading English. Silence has to leave the data alone, and it
     * must not fall back to a weaker read — that would restore exactly the answer this
     * refuses to trust.
     */
    @Test
    fun `a device that cannot state a region does not fire`() {
        assertNull(legacyRelabelCurrency(region(null)))
    }

    /**
     * A currency the app does not offer falls into the silent case of leaving the
     * denomination alone — **not** into [CurrencyCatalog.FALLBACK_CURRENCY], which
     * would relabel a Japanese device's accounts to dollars.
     */
    @Test
    fun `a currency outside the offered set does not fire`() {
        assertNull(legacyRelabelCurrency(region("JPY")))
    }
}
