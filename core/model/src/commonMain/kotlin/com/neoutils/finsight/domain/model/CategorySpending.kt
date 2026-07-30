package com.neoutils.finsight.domain.model

import com.neoutils.finsight.extension.ConsolidatedFigure

/**
 * A category's movement in a period, already reduced to what a surface shows.
 *
 * [amount] is a [ConsolidatedFigure] rather than a number because a category is a dimension
 * and not an account: its entries may be denominated in several currencies, and the breakdown
 * has to both *rank* the categories against one another and *render* each one. Those are the
 * figure's two projections, and they come from a single decision about which quote governs
 * this number — computing one here and rebuilding the other in the screen would be two.
 *
 * [percentage] is a share of what the rates reached, on the 0..100 scale. A category no rate
 * could reach contributes nothing to the ranking and reads 0%, while its own figure is still
 * shown whole and carries the approximation mark. The share of a total that cannot be stated
 * in one currency is not a number this model may invent, and the mark beside it is what says
 * so.
 */
data class CategorySpending(
    val category: Category,
    val amount: ConsolidatedFigure,
    val percentage: Double
)
