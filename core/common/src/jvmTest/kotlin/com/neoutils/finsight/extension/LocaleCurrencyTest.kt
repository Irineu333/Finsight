package com.neoutils.finsight.extension

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

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
    fun `the language does not decide the currency`() {
        // An interface in English on a device whose region is Brazil is still BRL.
        // It is what keeps the legacy relabelling from firing on someone who merely
        // reads English (design D30).
        assertEquals("BRL", withLocale(Locale("en", "BR")) { localeCurrencyCode() })
        assertEquals("USD", withLocale(Locale("pt", "US")) { localeCurrencyCode() })
    }
}
