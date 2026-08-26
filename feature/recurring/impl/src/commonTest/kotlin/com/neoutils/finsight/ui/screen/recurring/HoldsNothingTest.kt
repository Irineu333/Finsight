package com.neoutils.finsight.ui.screen.recurring

import com.neoutils.finsight.extension.ConsolidatedAmount
import com.neoutils.finsight.extension.DisplayAmount
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * **What decides whether a block of the month summary opens folded.**
 *
 * A block whose two figures are zero says nothing its own label does not already say, so
 * it starts collapsed. The predicate has to be about *every* term: a figure may hold a
 * zero in one currency beside real money in another, and that is a month with something
 * in it.
 */
class HoldsNothingTest {

    private fun figure(vararg terms: Pair<String, Double>) = ConsolidatedAmount(
        terms = terms.map { (currency, value) ->
            DisplayAmount.magnitude(value, currency, isApproximate = false)
        },
        isApproximate = false,
    )

    @Test
    fun `two zeros hold nothing`() {
        assertTrue(holdsNothing(figure("BRL" to 0.0), figure("BRL" to 0.0)))
    }

    @Test
    fun `money on either side is something`() {
        assertFalse(holdsNothing(figure("BRL" to 1_240.0), figure("BRL" to 0.0)))
        assertFalse(holdsNothing(figure("BRL" to 0.0), figure("BRL" to 865.0)))
    }

    /**
     * The multi-currency reading, and the one a naive check gets wrong: the reducer
     * returns a term per currency, and a zero beside an amount is not an empty month.
     */
    @Test
    fun `a zero term beside a real one is not nothing`() {
        val mixed = figure("BRL" to 0.0, "USD" to 50.0)

        assertFalse(holdsNothing(mixed, figure("BRL" to 0.0)))
    }
}
