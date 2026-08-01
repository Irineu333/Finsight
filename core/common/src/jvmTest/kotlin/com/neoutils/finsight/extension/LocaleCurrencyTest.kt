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
    fun `the country of the locale decides, not its language`() {
        // An interface in English on a locale whose country is Brazil is still BRL.
        // What this does *not* prove is that the user is in Brazil: on Android the
        // country of the locale is the country attached to the chosen language, which
        // is why the legacy relabelling of design D30 asks `DeviceRegion` instead.
        assertEquals("BRL", withLocale(Locale("en", "BR")) { localeCurrencyCode() })
        assertEquals("USD", withLocale(Locale("pt", "US")) { localeCurrencyCode() })
    }

    /**
     * The desktop's region, which the JVM takes from the operating system's region
     * setting. It is the same answer as the locale's here — and it is a separate type
     * because it is *not* the same answer on Android, where only this one may decide a
     * relabelling.
     */
    @Test
    fun `the desktop region names its currency`() {
        assertEquals("USD", withLocale(Locale("en", "US")) { LocaleDeviceRegion().currencyCode() })
        assertEquals("BRL", withLocale(Locale("en", "BR")) { LocaleDeviceRegion().currencyCode() })
    }

    @Test
    fun `a locale with no country states no region`() {
        assertNull(withLocale(Locale("en")) { LocaleDeviceRegion().currencyCode() })
    }
}
