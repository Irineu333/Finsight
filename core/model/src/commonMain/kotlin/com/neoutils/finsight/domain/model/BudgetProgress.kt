package com.neoutils.finsight.domain.model

import com.neoutils.finsight.extension.DisplayAmount

data class BudgetProgress(
    val budget: Budget,
    /** The spending, expressed in the limit's own currency (design D13). */
    val spent: Double,
    /** Whether reaching that currency took a rate. */
    val isApproximate: Boolean = false,
    /**
     * Whether part of the spending sits in a currency no rate reaches, and is therefore
     * **not** in [spent].
     *
     * It makes the bar a floor rather than a measurement, and the surface has to be able
     * to say so: silently leaving that part out reads as "less spent than you have",
     * which is the one direction a budget must never err in.
     */
    val hasUnpricedSpending: Boolean = false,
    val recurringLabel: String? = null,
    val recurring: Recurring? = null,
) {
    val progress: Float get() = (spent / budget.amount).coerceIn(0.0, 1.0).toFloat()
    val remaining: Double get() = (budget.amount - spent).coerceAtLeast(0.0)
    val isExceeded: Boolean get() = spent > budget.amount

    /**
     * The three derived figures as amounts that **carry the mark this progress derived**,
     * so that no screen has to remember to attach it — and none of the three that show a
     * budget can disagree with the others.
     *
     * All three inherit [isApproximate], because all three are computed from [spent]: if
     * reaching the limit's currency took a rate, then what was spent, what is left and by
     * how much it was exceeded are equally approximate.
     */
    val spentAmount: DisplayAmount
        get() = DisplayAmount.magnitude(spent, budget.currency, isApproximate)

    val remainingAmount: DisplayAmount
        get() = DisplayAmount.magnitude(remaining, budget.currency, isApproximate)

    val exceededAmount: DisplayAmount
        get() = DisplayAmount.magnitude(spent - budget.amount, budget.currency, isApproximate)

    /**
     * The limit, which is **never** approximate: the user typed it, in a currency chosen
     * once and never re-denominated (design D13). It is here so the pair reads from one
     * place, not because it could ever carry a mark.
     */
    val limitAmount: DisplayAmount
        get() = DisplayAmount.magnitude(budget.amount, budget.currency, isApproximate = false)
}
