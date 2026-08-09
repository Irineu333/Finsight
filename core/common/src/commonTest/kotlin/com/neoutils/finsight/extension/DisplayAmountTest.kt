package com.neoutils.finsight.extension

import com.neoutils.finsight.extension.DisplayAmount.SignPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Pins each policy's reading. Assertions are stated as *relations* to
 * `CurrencyFormatter.format`, never as literal text: the currency now decides the symbol
 * but the *locale* still decides separators and where that symbol sits, so a literal
 * would pin the machine that ran the test rather than the rule.
 */
class DisplayAmountTest {

    private val formatter = currencyFormatterOf(TEST_SYMBOLS)

    private fun format(amount: DisplayAmount) = formatter.format(amount)

    @Test
    fun magnitudeDropsTheSign() {
        assertEquals(formatter.format(100.0, BRL), format(magnitude(-100.0)))
        assertEquals(100.0, magnitude(-100.0).value)
    }

    @Test
    fun naturalShowsOnlyTheNegative() {
        assertEquals(formatter.format(100.0, BRL), format(natural(100.0)))
        assertEquals(formatter.format(-100.0, BRL), format(natural(-100.0)))
    }

    @Test
    fun neutralAddsNothing() {
        assertEquals(formatter.format(100.0, BRL), format(neutral(100.0)))
    }

    @Test
    fun explicitSignSpellsBothDirectionsOut() {
        assertEquals("+" + formatter.format(100.0, BRL), format(explicitSign(100.0)))
        assertEquals("-" + formatter.format(100.0, BRL), format(explicitSign(-100.0)))
    }

    @Test
    fun explicitSignOfZeroShowsNoSign() {
        assertEquals(formatter.format(0.0, BRL), format(explicitSign(0.0)))
    }

    @Test
    fun forcedDirectionsIgnoreTheIncomingSign() {
        assertEquals("+" + formatter.format(100.0, BRL), format(forcedPositive(-100.0)))
        assertEquals("-" + formatter.format(100.0, BRL), format(forcedNegative(100.0)))
    }

    @Test
    fun forcedDirectionsCarryTheSignTheyShow() {
        assertEquals(100.0, forcedPositive(-100.0).value)
        assertEquals(-100.0, forcedNegative(100.0).value)
    }

    @Test
    fun owedReadsTheDebtBehindALedgerBalance() {
        assertEquals(formatter.format(100.0, BRL), format(owed(-100.0)))
    }

    @Test
    fun owedReadsZeroWhenNothingIsOwed() {
        assertEquals(formatter.format(0.0, BRL), format(owed(100.0)))
        assertEquals(0.0, owed(100.0).value)
    }

    @Test
    fun policyTravelsWithTheValue() {
        assertEquals(SignPolicy.EXPLICIT_SIGN, explicitSign(1.0).policy)
        assertEquals(magnitude(1.0), magnitude(1.0))
    }

    @Test
    fun currencyTravelsWithTheValue() {
        assertEquals(BRL, natural(1.0).currency)
        assertEquals(USD, DisplayAmount.natural(1.0, USD, isApproximate = false).currency)
    }

    @Test
    fun twoCurrenciesAreNotTheSameFigure() {
        assertNotEquals(
            DisplayAmount.natural(1.0, BRL, isApproximate = false),
            DisplayAmount.natural(1.0, USD, isApproximate = false),
        )
        assertNotEquals(
            DisplayAmount.natural(1.0, BRL, isApproximate = false).hashCode(),
            DisplayAmount.natural(1.0, USD, isApproximate = false).hashCode(),
        )
    }

    @Test
    fun exactnessTravelsWithTheValue() {
        assertEquals(false, natural(1.0).isApproximate)
        assertEquals(true, DisplayAmount.natural(1.0, BRL, isApproximate = true).isApproximate)
        assertNotEquals(
            DisplayAmount.natural(1.0, BRL, isApproximate = false),
            DisplayAmount.natural(1.0, BRL, isApproximate = true),
        )
    }

    @Test
    fun theSymbolComesFromTheCurrencyAndNotTheLocale() {
        // Whatever this machine's locale is, two currencies cannot render alike.
        assertNotEquals(formatter.format(100.0, BRL), formatter.format(100.0, USD))
    }

    @Test
    fun theApproximationMarkIsOuterThanTheSign() {
        val approximate = DisplayAmount.explicitSign(100.0, BRL, isApproximate = true)

        assertEquals("$APPROXIMATION_MARK +${formatter.format(100.0, BRL)}", format(approximate))
    }

    @Test
    fun anExactFigureCarriesNoMark() {
        assertEquals(formatter.format(100.0, BRL), format(natural(100.0)))
    }

    @Test
    fun aTermOfAFigureCanSuppressTheRepeatedMark() {
        // One figure carries one mark; only the multi-term renderer asks for this.
        val term = DisplayAmount.natural(50.0, USD, isApproximate = true)

        assertEquals(formatter.format(50.0, USD), formatter.format(term, withMark = false))
    }

    private companion object {
        const val BRL = "BRL"
        const val USD = "USD"

        fun magnitude(value: Double) = DisplayAmount.magnitude(value, BRL, isApproximate = false)
        fun natural(value: Double) = DisplayAmount.natural(value, BRL, isApproximate = false)
        fun neutral(value: Double) = DisplayAmount.neutral(value, BRL, isApproximate = false)
        fun explicitSign(value: Double) =
            DisplayAmount.explicitSign(value, BRL, isApproximate = false)

        fun forcedPositive(value: Double) =
            DisplayAmount.forcedPositive(value, BRL, isApproximate = false)

        fun forcedNegative(value: Double) =
            DisplayAmount.forcedNegative(value, BRL, isApproximate = false)

        fun owed(value: Double) = DisplayAmount.owed(value, BRL, isApproximate = false)
    }
}
