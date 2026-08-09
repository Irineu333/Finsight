package com.neoutils.finsight.extension

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins what the **platform** answers about one code — the half that lets a stored row go
 * unnamed and still read in the current language. Which currencies exist is not asked
 * here, and never is: that is a table, above this module.
 */
class PlatformCurrencyTest {

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
    fun `names a code in the current language`() {
        val inEnglish = withLocale(Locale("en", "US")) { platformCurrency("BRL") }
        val inPortuguese = withLocale(Locale("pt", "BR")) { platformCurrency("BRL") }

        assertEquals("Brazilian Real", inEnglish?.name)
        assertEquals("Real brasileiro", inPortuguese?.name)
    }

    @Test
    fun `an unknown code answers absence instead of throwing`() {
        assertNull(platformCurrency("MILHAS"))
        assertNull(platformCurrency(""))
    }

    @Test
    fun `states the decimal places the app's arithmetic depends on`() {
        assertEquals(2, platformCurrency("USD")?.fractionDigits)
        assertEquals(0, platformCurrency("JPY")?.fractionDigits)
        assertEquals(3, platformCurrency("KWD")?.fractionDigits)
    }

    @Test
    fun `two decimal places is the premise, and an invented code does not contradict it`() {
        assertTrue(isTwoDecimalCurrency("BRL"))
        assertTrue(isTwoDecimalCurrency("MILHAS"))
        assertEquals(false, isTwoDecimalCurrency("JPY"))
        assertEquals(false, isTwoDecimalCurrency("KWD"))
    }

    @Test
    fun `suggests a symbol, and the code is the worst case`() {
        assertEquals("R$", withLocale(Locale("pt", "BR")) { platformCurrency("BRL") }?.symbol)
    }

    @Test
    fun `the code is normalised before the platform is asked`() {
        assertEquals("USD", platformCurrency("usd")?.code)
    }
}
