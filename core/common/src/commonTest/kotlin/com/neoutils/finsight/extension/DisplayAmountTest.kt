package com.neoutils.finsight.extension

import com.neoutils.finsight.extension.DisplayAmount.SignPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Pins each policy's reading. Assertions are stated as *relations* to
 * `CurrencyFormatter.format`, never as currency literals: the formatter follows the
 * platform's default locale for the *format*, so a literal would pin the machine that ran
 * the test rather than the rule.
 */
class DisplayAmountTest {

    private val formatter = CurrencyFormatter()

    private val brl = Denomination.exact("BRL")
    private val usd = Denomination.exact("USD")

    private fun formatted(value: Double, currency: String = "BRL") =
        formatter.format(value, currency)

    @Test
    fun magnitudeDropsTheSign() {
        assertEquals(formatted(100.0), formatter.format(DisplayAmount.magnitude(-100.0, brl)))
        assertEquals(100.0, DisplayAmount.magnitude(-100.0, brl).value)
    }

    @Test
    fun naturalShowsOnlyTheNegative() {
        assertEquals(formatted(100.0), formatter.format(DisplayAmount.natural(100.0, brl)))
        assertEquals(formatted(-100.0), formatter.format(DisplayAmount.natural(-100.0, brl)))
    }

    @Test
    fun neutralAddsNothing() {
        assertEquals(formatted(100.0), formatter.format(DisplayAmount.neutral(100.0, brl)))
    }

    @Test
    fun explicitSignSpellsBothDirectionsOut() {
        assertEquals("+" + formatted(100.0), formatter.format(DisplayAmount.explicitSign(100.0, brl)))
        assertEquals("-" + formatted(100.0), formatter.format(DisplayAmount.explicitSign(-100.0, brl)))
    }

    @Test
    fun explicitSignOfZeroShowsNoSign() {
        assertEquals(formatted(0.0), formatter.format(DisplayAmount.explicitSign(0.0, brl)))
    }

    @Test
    fun forcedDirectionsIgnoreTheIncomingSign() {
        assertEquals("+" + formatted(100.0), formatter.format(DisplayAmount.forcedPositive(-100.0, brl)))
        assertEquals("-" + formatted(100.0), formatter.format(DisplayAmount.forcedNegative(100.0, brl)))
    }

    @Test
    fun forcedDirectionsCarryTheSignTheyShow() {
        assertEquals(100.0, DisplayAmount.forcedPositive(-100.0, brl).value)
        assertEquals(-100.0, DisplayAmount.forcedNegative(100.0, brl).value)
    }

    @Test
    fun owedReadsTheDebtBehindALedgerBalance() {
        assertEquals(formatted(100.0), formatter.format(DisplayAmount.owed(-100.0, brl)))
    }

    @Test
    fun owedReadsZeroWhenNothingIsOwed() {
        assertEquals(formatted(0.0), formatter.format(DisplayAmount.owed(100.0, brl)))
        assertEquals(0.0, DisplayAmount.owed(100.0, brl).value)
    }

    @Test
    fun policyTravelsWithTheValue() {
        assertEquals(SignPolicy.EXPLICIT_SIGN, DisplayAmount.explicitSign(1.0, brl).policy)
        assertEquals(DisplayAmount.magnitude(1.0, brl), DisplayAmount.magnitude(1.0, brl))
    }

    @Test
    fun denominationTravelsWithTheValue() {
        assertEquals("USD", DisplayAmount.natural(1.0, usd).currency)
        assertNotEquals(DisplayAmount.magnitude(1.0, brl), DisplayAmount.magnitude(1.0, usd))
    }

    @Test
    fun currencyComesFromTheValueAndNotFromTheLocale() {
        assertEquals(formatted(100.0, "USD"), formatter.format(DisplayAmount.natural(100.0, usd)))
        assertNotEquals(
            formatter.format(DisplayAmount.natural(100.0, brl)),
            formatter.format(DisplayAmount.natural(100.0, usd)),
        )
    }

    @Test
    fun exactFiguresCarryNoMark() {
        assertEquals(formatted(100.0), formatter.format(DisplayAmount.natural(100.0, brl)))
    }

    @Test
    fun theApproximationMarkIsOutermost() {
        val approximate = Denomination.approximate("BRL")
        assertEquals(
            "≈ +" + formatted(1240.0),
            formatter.format(DisplayAmount.forcedPositive(1240.0, approximate)),
        )
        assertEquals(
            "≈ -" + formatted(1240.0),
            formatter.format(DisplayAmount.explicitSign(-1240.0, approximate)),
        )
        assertEquals(
            "≈ " + formatted(1240.0),
            formatter.format(DisplayAmount.natural(1240.0, approximate)),
        )
    }

    @Test
    fun exactnessTravelsWithTheValue() {
        assertNotEquals(
            DisplayAmount.natural(1.0, Denomination.exact("BRL")),
            DisplayAmount.natural(1.0, Denomination.approximate("BRL")),
        )
    }
}
