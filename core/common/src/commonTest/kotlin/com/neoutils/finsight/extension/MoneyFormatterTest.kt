package com.neoutils.finsight.extension

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the round trip of a money field's text. The seed a modal writes and the text the
 * field rewrites on every keystroke now come from the same rule, so the property that
 * matters is that reading back what was written returns the amount untouched — sign
 * included, which is where the hand-rolled copies used to differ from each other.
 */
class MoneyFormatterTest {

    private val formatter = CurrencyFormatter()

    @Test
    fun `what is written reads back as the same amount`() {
        listOf(0L, 1L, 500L, 123456L, -500L, -123456L).forEach { cents ->
            assertEquals(
                cents.toDouble() / 100,
                formatter.moneyInput(cents, "BRL").moneyToDouble(),
                "round trip of $cents cents",
            )
        }
    }

    @Test
    fun `a negative is spelled where the reader looks for it`() {
        // `moneyToDouble` decides by the leading `-`, so the sign has to sit outside the
        // formatted magnitude — wherever the locale would otherwise put it.
        assertTrue(formatter.moneyInput(-500L, "BRL").startsWith("-"))
        assertEquals("-" + formatter.format(5.0, "BRL"), formatter.moneyInput(-500L, "BRL"))
    }

    @Test
    fun `the currency is the caller's and the magnitude is the same`() {
        assertEquals(formatter.format(1234.56, "USD"), formatter.moneyInput(123456L, "USD"))
    }
}
