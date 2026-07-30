package com.neoutils.finsight.domain.model

import com.neoutils.finsight.extension.ConsolidatedFigure

/**
 * How much of a budget has been spent, against a limit stated in one currency.
 *
 * [spent] is reduced to **the limit's** currency and not to the base: the limit is a number
 * the user typed, and comparing it against a total expressed in something else is the bar
 * comparing two things. The reduction is the consolidation layer's, so what arrives here is
 * already one decision with two projections — the figure the label prints, and the number the
 * bar divides.
 *
 * The bar, the remainder and the "exceeded" test all read [ConsolidatedFigure.comparable],
 * which leaves out whatever no rate reached — which is why [isApproximate] sits beside them
 * rather than being rederived by the surface. A progress bar that moved because a quote moved
 * is the accepted cost of there being a single number, and it is paid only by a budget whose
 * spending actually crosses currencies.
 */
data class BudgetProgress(
    val budget: Budget,
    val spent: ConsolidatedFigure,
    val recurringLabel: String? = null,
    val recurring: Recurring? = null,
) {
    val progress: Float get() = (spent.comparable / budget.amount).coerceIn(0.0, 1.0).toFloat()
    val remaining: Double get() = (budget.amount - spent.comparable).coerceAtLeast(0.0)
    val isExceeded: Boolean get() = spent.comparable > budget.amount

    /** Whether any figure derived from [spent] is less than exact — the bar included. */
    val isApproximate: Boolean get() = spent.isApproximate
}
