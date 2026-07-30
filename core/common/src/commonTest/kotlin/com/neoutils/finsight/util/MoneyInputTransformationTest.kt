package com.neoutils.finsight.util

import com.neoutils.finsight.extension.CurrencyFormatter
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the rule that lets a filled field survive a change of account. As elsewhere in
 * `:core:common`, assertions are relations to `CurrencyFormatter.format` rather than currency
 * literals: the locale governs the format, and a literal would pin the machine.
 */
class MoneyInputTransformationTest {

    private val formatter = CurrencyFormatter()

    private fun transformation(currency: String) = MoneyInputTransformation(currency, formatter)

    @Test
    fun `digits are read as cents`() {
        assertEquals(formatter.format(1234.56, "BRL"), transformation("BRL").reformat("123456"))
    }

    @Test
    fun `reformatting what it wrote changes nothing`() {
        val brl = transformation("BRL")
        val once = brl.reformat("123456")

        assertEquals(once, brl.reformat(once))
    }

    @Test
    fun `a filled field re-reads in the new currency without changing the amount`() {
        val typed = transformation("BRL").reformat("123456")

        // The very move the field makes when the user picks a dollar account under it: the
        // amount the user entered is the same 1234.56, and only its denomination moved.
        assertEquals(formatter.format(1234.56, "USD"), transformation("USD").reformat(typed))
    }

    @Test
    fun `a negative amount keeps its direction across the change`() {
        val typed = transformation("BRL").reformat("-123456")

        assertEquals("-" + formatter.format(1234.56, "BRL"), typed)
        assertEquals("-" + formatter.format(1234.56, "USD"), transformation("USD").reformat(typed))
    }

    @Test
    fun `text with no digit reads as nothing`() {
        assertEquals("", transformation("BRL").reformat(""))
        assertEquals("", transformation("BRL").reformat("R$"))
    }
}
