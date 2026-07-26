package com.neoutils.finsight.extension

import com.neoutils.finsight.extension.DisplayAmount.SignPolicy
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins each policy's reading. Assertions are stated as *relations* to
 * `CurrencyFormatter.format`, never as currency literals: the formatter follows the
 * platform's default locale, so a literal would pin the machine that ran the test
 * rather than the rule.
 */
class DisplayAmountTest {

    private val formatter = CurrencyFormatter()

    @Test
    fun magnitudeDropsTheSign() {
        assertEquals(formatter.format(100.0), formatter.format(DisplayAmount.magnitude(-100.0)))
        assertEquals(100.0, DisplayAmount.magnitude(-100.0).value)
    }

    @Test
    fun naturalShowsOnlyTheNegative() {
        assertEquals(formatter.format(100.0), formatter.format(DisplayAmount.natural(100.0)))
        assertEquals(formatter.format(-100.0), formatter.format(DisplayAmount.natural(-100.0)))
    }

    @Test
    fun neutralAddsNothing() {
        assertEquals(formatter.format(100.0), formatter.format(DisplayAmount.neutral(100.0)))
    }

    @Test
    fun explicitSignSpellsBothDirectionsOut() {
        assertEquals("+" + formatter.format(100.0), formatter.format(DisplayAmount.explicitSign(100.0)))
        assertEquals("-" + formatter.format(100.0), formatter.format(DisplayAmount.explicitSign(-100.0)))
    }

    @Test
    fun explicitSignOfZeroShowsNoSign() {
        assertEquals(formatter.format(0.0), formatter.format(DisplayAmount.explicitSign(0.0)))
    }

    @Test
    fun forcedDirectionsIgnoreTheIncomingSign() {
        assertEquals("+" + formatter.format(100.0), formatter.format(DisplayAmount.forcedPositive(-100.0)))
        assertEquals("-" + formatter.format(100.0), formatter.format(DisplayAmount.forcedNegative(100.0)))
    }

    @Test
    fun forcedDirectionsCarryTheSignTheyShow() {
        assertEquals(100.0, DisplayAmount.forcedPositive(-100.0).value)
        assertEquals(-100.0, DisplayAmount.forcedNegative(100.0).value)
    }

    @Test
    fun owedReadsTheDebtBehindALedgerBalance() {
        assertEquals(formatter.format(100.0), formatter.format(DisplayAmount.owed(-100.0)))
    }

    @Test
    fun owedReadsZeroWhenNothingIsOwed() {
        assertEquals(formatter.format(0.0), formatter.format(DisplayAmount.owed(100.0)))
        assertEquals(0.0, DisplayAmount.owed(100.0).value)
    }

    @Test
    fun policyTravelsWithTheValue() {
        assertEquals(SignPolicy.EXPLICIT_SIGN, DisplayAmount.explicitSign(1.0).policy)
        assertEquals(DisplayAmount.magnitude(1.0), DisplayAmount.magnitude(1.0))
    }
}
