package com.neoutils.finsight.ui.model

import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.extension.MoneyFigure

/**
 * A category's share of a period, ready to render: the category itself, the figure it spent
 * or earned, and how much of the whole that is.
 *
 * It exists because the domain's `CategorySpending` cannot carry the figure denominated —
 * money as it *reads* is presentation, and the layer rule keeps it out of the domain. What
 * the domain owns is the number and the share; what this owns is how they are shown.
 *
 * [amount] is a [MoneyFigure] rather than a single term because a category's spending spans
 * every account: it is one of the figures the consolidation layer reduces, and one a rate may
 * leave with a term of its own.
 *
 * [percentage] is a share of the whole, on the 0..100 scale the progress bar reads — a ratio
 * is denominated by nothing, and it stays a plain number.
 */
data class CategorySpendingUi(
    val category: Category,
    val amount: MoneyFigure,
    val percentage: Double,
)
