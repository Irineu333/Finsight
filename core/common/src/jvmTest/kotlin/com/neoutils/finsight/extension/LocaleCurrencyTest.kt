package com.neoutils.finsight.extension

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Pins the half of the resolution this module owns: what the **device** says.
 *
 * Reducing that answer to a currency the app is willing to denominate an account in
 * is the catalog's job, in `:core:model`, and is tested there — this module knows
 * nothing about which currencies the app offers.
 */
class LocaleCurrencyTest {

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
    fun `the region decides the currency`() {
        assertEquals("USD", withLocale(Locale("en", "US")) { localeCurrencyCode() })
        assertEquals("BRL", withLocale(Locale("pt", "BR")) { localeCurrencyCode() })
    }

    @Test
    fun `the country of the locale decides and not its language`() {
        // An interface in English on a locale whose country is Brazil is still BRL, and
        // the reverse is what a user in that locale has been *reading* — which is the
        // whole of what this answer is used for, in the base currency and in the legacy
        // relabelling of design D30 alike.
        assertEquals("BRL", withLocale(Locale("en", "BR")) { localeCurrencyCode() })
        assertEquals("USD", withLocale(Locale("pt", "US")) { localeCurrencyCode() })
    }

    /**
     * A locale with no country names no currency, and the callers read that as silence:
     * the base falls back to its own default, and nothing is relabelled.
     */
    @Test
    fun `a locale with no country names no currency`() {
        assertNull(withLocale(Locale("en")) { localeCurrencyCode() })
    }
}
