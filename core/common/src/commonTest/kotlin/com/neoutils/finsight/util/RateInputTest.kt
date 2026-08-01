package com.neoutils.finsight.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * A rate is typed left to right, and it keeps every place it was typed with.
 *
 * The rule these tests hold down is the one the money rule broke: filling from the right
 * turned `5,32` into `0,0532`, and two decimal places made a rate like `0,000691` — a
 * real rate of a currency this app offers — round to zero on the way in. The archive is
 * required to hold the full quotient (`currency-consolidation`), and a field that cannot
 * express it is where that requirement was being lost.
 */
class RateInputTest {

    private val comma = RateInputTransformation(separator = ',')
    private val dot = RateInputTransformation(separator = '.')

    @Test
    fun `what is typed is what stays`() {
        assertEquals("5,32", comma.filterTyped("5,32"))
        assertEquals("5,5", comma.filterTyped("5,5"))
        assertEquals("0,000691", comma.filterTyped("0,000691"))
    }

    @Test
    fun `the keyboard separator becomes the language separator`() {
        // A dot typed on an English keyboard under a pt-BR locale. Without this, the
        // character is dropped and `5.32` reads five hundred and thirty-two.
        assertEquals("5,32", comma.filterTyped("5.32"))
        assertEquals("5.32", dot.filterTyped("5,32"))
    }

    @Test
    fun `only one separator survives`() {
        assertEquals("5,32", comma.filterTyped("5,3,2"))
    }

    @Test
    fun `a thousands mark is not a decimal point`() {
        // The defect this closes: pasted from a rate site, or written the way the
        // language writes thousands, `1.450,50` read as `1,45050` — a thousandfold
        // error saved as the user's own rate, which then outranks every observation.
        assertEquals("1450,50", comma.filterTyped("1.450,50"))
        assertEquals("1450.50", dot.filterTyped("1,450.50"))
        assertEquals(1450.5, "1.450,50".rateToDoubleOrNull(','))
        assertEquals(1450.5, "1,450.50".rateToDoubleOrNull('.'))
    }

    @Test
    fun `grouping is dropped however many marks there are`() {
        assertEquals("1234567,89", comma.filterTyped("1.234.567,89"))
    }

    @Test
    fun `a lone separator is still the decimal point`() {
        // Nothing corroborates a thousands mark here, and a rate of one and forty-five
        // hundredths is the ordinary reading — this is the unchanged case.
        assertEquals("1,45", comma.filterTyped("1.45"))
        assertEquals("1,450", comma.filterTyped("1.450"))
    }

    @Test
    fun `a leading separator means below one`() {
        assertEquals("0,5", comma.filterTyped(",5"))
    }

    @Test
    fun `anything that is not a digit or a separator is refused`() {
        assertEquals("532", comma.filterTyped("R$ 5x3-2"))
    }

    @Test
    fun `no more places than a rate may carry`() {
        val typed = "0," + "1".repeat(RATE_SCALE + 3)
        assertEquals("0," + "1".repeat(RATE_SCALE), comma.filterTyped(typed))
    }

    @Test
    fun `an empty field says nothing rather than zero`() {
        assertNull("".rateToDoubleOrNull(','))
        assertNull("R$".rateToDoubleOrNull(','))
    }

    @Test
    fun `a rate below one cent is a number and not a rounded zero`() {
        // The whole point: under the money rule this parsed to 0.0 and the form refused
        // to save a rate that is perfectly ordinary for several catalog currencies.
        assertEquals(0.000691, "0,000691".rateToDoubleOrNull(','))
    }

    @Test
    fun `a half typed number is the part that is there`() {
        assertEquals(5.0, "5,".rateToDoubleOrNull(','))
    }

    @Test
    fun `what the field accepts is what it reads back`() {
        val typed = comma.filterTyped("5,4321")
        assertEquals(5.4321, typed.rateToDoubleOrNull(','))
    }
}
