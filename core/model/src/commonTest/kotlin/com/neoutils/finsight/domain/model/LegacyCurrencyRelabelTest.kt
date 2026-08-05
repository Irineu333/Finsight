package com.neoutils.finsight.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Which devices the legacy relabelling fires on — and, more to the point, which it
 * does not.
 *
 * The resolution is where the false positive of design D30 lives, so it is where the
 * rule has to be proved: what the device names is adopted, because it is what the user
 * has been reading; a device that names nothing is left alone; and the two-decimal
 * premise bars a currency the app cannot denominate an account in, instead of coercing
 * it into the currency of last resort.
 *
 * The code arrives as a value here rather than being read from the platform. The reading
 * is a single call to `localeCurrencyCode()` — everything that can go wrong is the rule.
 */
class LegacyCurrencyRelabelTest {

    @Test
    fun `a foreign denomination relabels`() {
        assertEquals("USD", legacyRelabelCurrency("USD"))
        assertEquals("EUR", legacyRelabelCurrency("EUR"))
    }

    @Test
    fun `the legacy denomination is not touched`() {
        assertNull(legacyRelabelCurrency(LEGACY_DENOMINATION))
    }

    /**
     * The interface language is not excluded, and that is the decision.
     *
     * On Android there is no language without a country, so *English (United States)* is
     * `en-US` — and that user has been reading `$` over their reais for as long as they
     * have had the app, because the old formatter read the same locale. Relabelling is
     * what leaves their screen alone; keeping BRL is what would change it.
     */
    @Test
    fun `what the device names is adopted whatever it names`() {
        assertEquals("USD", legacyRelabelCurrency("usd"))
    }

    /**
     * A device that names no currency says nothing, and nothing is what happens: there is
     * no weaker read to fall back to and no currency of last resort to land on.
     */
    @Test
    fun `a device that names no currency does not fire`() {
        assertNull(legacyRelabelCurrency(null))
    }

    /**
     * A currency outside the base-100 premise falls into the silent case of leaving the
     * denomination alone — **not** into [FALLBACK_CURRENCY], which would relabel a
     * Japanese device's accounts to dollars.
     */
    @Test
    fun `a currency outside the two-decimal premise does not fire`() {
        assertNull(legacyRelabelCurrency("JPY"))
    }
}
