package com.neoutils.finsight.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The mark a budget's progress derived reaches the three figures a screen shows.
 *
 * It lives on the model, and not on each of the three surfaces that draw a budget, for the
 * reason `money-display` gives: the screen does not decide the mark. Three screens each
 * attaching it by hand is three chances to forget — and forgetting was the state this test
 * was written for: the domain derived `isApproximate` and `hasUnpricedSpending`, the tests
 * asserted them, and no surface read either.
 */
class BudgetProgressMarkTest {

    private fun budget(amount: Double, currency: String) = Budget(
        id = 1,
        title = "Alimentação",
        categories = emptyList(),
        iconKey = "shopping",
        amount = amount,
        currency = currency,
        limitType = LimitType.FIXED,
        createdAt = 0L,
    )

    @Test
    fun `every figure derived from the spending inherits its mark`() {
        val progress = BudgetProgress(
            budget = budget(1000.0, "BRL"),
            spent = 400.0,
            isApproximate = true,
        )

        assertTrue(progress.spentAmount!!.isApproximate)
        assertTrue(progress.remainingAmount!!.isApproximate, "600 is 1000 minus an approximate 400")
        assertEquals(600.0, progress.remainingAmount!!.value)
    }

    @Test
    fun `what was exceeded by is approximate for the same reason`() {
        val progress = BudgetProgress(
            budget = budget(1000.0, "BRL"),
            spent = 1200.0,
            isApproximate = true,
        )

        assertTrue(progress.isExceeded)
        assertTrue(progress.exceededAmount!!.isApproximate)
        assertEquals(200.0, progress.exceededAmount!!.value)
    }

    /**
     * The limit never carries the mark, whatever the spending did: the user typed it, in a
     * currency chosen once and never re-denominated (design D13).
     */
    @Test
    fun `the limit is never approximate`() {
        val progress = BudgetProgress(
            budget = budget(1000.0, "BRL"),
            spent = 400.0,
            isApproximate = true,
        )

        assertFalse(progress.limitAmount.isApproximate)
        assertEquals(1000.0, progress.limitAmount.value)
    }

    /** The single-currency user pays nothing for any of this. */
    @Test
    fun `an exact progress marks nothing`() {
        val progress = BudgetProgress(budget = budget(1000.0, "USD"), spent = 400.0)

        assertFalse(progress.spentAmount!!.isApproximate)
        assertFalse(progress.remainingAmount!!.isApproximate)
        assertEquals("USD", progress.spentAmount!!.currency, "denominated by the limit, not the base")
    }

    /**
     * Unpriced spending is not a small number, it is **no** number: `spent` is only what
     * could be priced, so every figure built on it is a floor. A floor shown as a total
     * reads "you spent less than you have" — the one direction a budget must never err in
     * — so the figures become `null`, which a surface renders as `***`, and the bar has no
     * fraction to draw.
     */
    @Test
    fun `unpriced spending leaves no figure and no fraction`() {
        val progress = BudgetProgress(
            budget = budget(1000.0, "BRL"),
            spent = 400.0,
            isApproximate = true,
            hasUnpricedSpending = true,
        )

        assertFalse(progress.isResolved)
        assertNull(progress.spentAmount, "a floor is not the total, so it is not offered as one")
        assertNull(progress.remainingAmount)
        assertNull(progress.exceededAmount)
        assertNull(progress.progress, "no fraction, so the surface draws no bar")
        assertFalse(progress.isExceeded, "what is not resolved is not known to be exceeded")
        assertEquals(1000.0, progress.limitAmount.value, "the limit is typed, and stays readable")
    }
}
