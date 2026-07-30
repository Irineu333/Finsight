package com.neoutils.finsight.domain.model

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Which devices the legacy relabelling fires on — and, more to the point, which it
 * does not.
 *
 * The resolution is where the false positive of design D30 is narrowed, so it is where
 * the narrowing has to be proved: the **region** decides rather than the language, and
 * the curated catalog bars a currency the app does not offer instead of coercing it
 * into the currency of last resort.
 */
class LegacyCurrencyRelabelTest {

    private fun <T> withLocale(locale: Locale, block: () -> T): T {
        val previous = Locale.getDefault()
        Locale.setDefault(locale)
        return try {
            block()
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `a foreign region relabels`() {
        assertEquals("USD", withLocale(Locale("en", "US")) { legacyRelabelCurrency() })
        assertEquals("EUR", withLocale(Locale("fr", "FR")) { legacyRelabelCurrency() })
    }

    @Test
    fun `the region of origin is not touched`() {
        assertNull(withLocale(Locale("pt", "BR")) { legacyRelabelCurrency() })
    }

    /**
     * An interface in English on a device whose region is Brazil is still the legacy
     * denomination. It is the narrowing that keeps the relabelling from firing on
     * someone who merely reads English.
     */
    @Test
    fun `language does not decide`() {
        assertNull(withLocale(Locale("en", "BR")) { legacyRelabelCurrency() })
    }

    /**
     * A currency the app does not offer falls into the silent case of leaving the
     * denomination alone — **not** into [CurrencyCatalog.FALLBACK_CURRENCY], which
     * would relabel a Japanese device's accounts to dollars.
     */
    @Test
    fun `a currency outside the offered set does not fire`() {
        assertNull(withLocale(Locale("ja", "JP")) { legacyRelabelCurrency() })
    }
}
