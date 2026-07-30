package com.neoutils.finsight.ui.model

import com.neoutils.finsight.extension.DisplayAmount
import com.neoutils.finsight.extension.MoneyFigure
import com.neoutils.finsight.ui.icons.CategoryLazyIcon

/**
 * A budget's progress as the bar shows it: what was spent, against what was set, and how far
 * along that is.
 *
 * It exists for the same reason as [CategorySpendingUi] — money as it *reads* is presentation,
 * and the layer rule keeps it out of the domain, which owns the numbers and the ratio.
 *
 * The two figures are deliberately of different types, and the difference is the rule. A limit
 * is set in one currency and stays in it, so it is a single [DisplayAmount]. Spending spans
 * every account, so it is a [MoneyFigure] a rate may leave with a term of its own — and the
 * label, whose grammar is "spent / limit", is a surface that holds one term. That is where the
 * declared degradation lands.
 *
 * [progress] is derived by the domain and copied, not recomputed here: a ratio between a figure
 * with an unconverted term and a limit is not something a screen can work out.
 */
data class BudgetProgressUi(
    val id: Long,
    val title: String,
    val icon: CategoryLazyIcon,
    val spent: MoneyFigure,
    val limit: DisplayAmount,
    val progress: Float,
)
