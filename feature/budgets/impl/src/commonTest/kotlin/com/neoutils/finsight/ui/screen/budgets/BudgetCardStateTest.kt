package com.neoutils.finsight.ui.screen.budgets

import com.neoutils.finsight.domain.model.Budget
import com.neoutils.finsight.domain.model.BudgetProgress
import com.neoutils.finsight.domain.model.LimitType
import com.neoutils.finsight.extension.ConsolidatedAmount
import com.neoutils.finsight.extension.DisplayAmount
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * **The two states the row has to get right without a label to lean on.**
 *
 * With the ceiling as the hero figure, the row no longer carries the "Limite" caption that
 * used to make a derived ceiling harmless, nor the "Restante"/"Excedido em" pair that used
 * to carry the overrun. Both states now depend on the row branching on the right property,
 * and both are silent failures if it does not: a derived ceiling printed bare claims
 * permanence over a number that re-derives itself every month, and a floor printed as a
 * total reads "you spent less than you have".
 */
class BudgetCardStateTest {

    private fun budget(
        limitType: LimitType = LimitType.FIXED,
        percentage: Double? = null,
        amount: Double = 300.0,
    ) = Budget(
        id = 1L,
        title = "Delivery",
        categories = emptyList(),
        iconKey = "shopping",
        amount = amount,
        currency = "BRL",
        limitType = limitType,
        percentage = percentage,
        recurringId = if (limitType == LimitType.PERCENTAGE) 7L else null,
        createdAt = 0L,
    )

    @Test
    fun `a percentage ceiling declares the share it derives from`() {
        val derived = budget(limitType = LimitType.PERCENTAGE, percentage = 30.0)

        assertEquals(30, derived.derivedLimitPercentage)
    }

    @Test
    fun `a typed ceiling carries no derivation mark`() {
        assertNull(budget(limitType = LimitType.FIXED).derivedLimitPercentage)
    }

    /**
     * The percentage travels beside the budget whatever the type says, so the type is what
     * decides — never the mere presence of a number. Reading it the other way round would
     * mark a ceiling the user typed.
     */
    @Test
    fun `a typed ceiling carries no mark even with a percentage left on the budget`() {
        val typed = budget(limitType = LimitType.FIXED, percentage = 30.0)

        assertNull(typed.derivedLimitPercentage)
    }

    /**
     * **The row is a surface of its own grammar**, in the sense of `money-display`: it has
     * the width of one amount and no more, so it shows the absence rather than the parts —
     * which is exactly the declaration that capability requires of each surface, and the
     * reason `spentFigure` is present here and deliberately unread.
     *
     * `spentAmount` being null is what the row renders as the absence mark, through
     * `formatOrUnresolved`; `progress` being null is what leaves the ring with its track
     * and no arc. An arc at zero would claim "nothing spent yet", which is precisely what
     * is not known.
     */
    @Test
    fun `unpriced spending shows the absence and draws no arc`() {
        val progress = BudgetProgress(
            budget = budget(),
            spent = 400.0,
            hasUnpricedSpending = true,
            spentFigure = ConsolidatedAmount(
                terms = listOf(
                    DisplayAmount.magnitude(400.0, "BRL", isApproximate = false),
                    DisplayAmount.magnitude(5_000.0, "JPY", isApproximate = false),
                ),
                isApproximate = true,
            ),
        )

        assertNull(progress.spentAmount)
        assertNull(progress.progress)
        // Nothing claims the ceiling was passed either: a floor is not a measurement.
        assertFalse(progress.isExceeded)
        // The parts are there, and the row is the surface that chooses not to show them.
        assertNotNull(progress.spentFigure)
    }

    /**
     * The ceiling is the one figure on the row no rate can take away: it was typed, in the
     * currency chosen when the budget was created. It stays whole while the spending
     * beside it is a placeholder.
     */
    @Test
    fun `the ceiling survives unpriced spending`() {
        val progress = BudgetProgress(
            budget = budget(amount = 300.0),
            spent = 400.0,
            hasUnpricedSpending = true,
        )

        assertEquals(300.0, progress.limitAmount.value)
        assertFalse(progress.limitAmount.isApproximate)
    }
}
