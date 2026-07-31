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
    /**
     * Whether the spending is a number at all.
     *
     * With part of it in a currency no rate reaches, [spent] is a **floor** — what could be
     * priced — and every figure built on it is a floor too. A floor shown as a total reads
     * "you spent less than you have", which is the one direction a budget must never err
     * in, so nothing built on it is offered as a value.
     */
    val isResolved: Boolean get() = !hasUnpricedSpending

    val remaining: Double get() = (budget.amount - spent).coerceAtLeast(0.0)

    /** True only when it is **known** to be exceeded; an unresolved floor never is. */
    val isExceeded: Boolean get() = isResolved && spent > budget.amount

    /**
     * How full the bar is, or `null` when there is no answer — the same vocabulary a
     * category share uses when the whole is unknown, and for the same reason. A surface
     * with no fraction shows no bar rather than an empty one: an empty bar is the claim
     * "nothing spent yet", which is exactly what is not known here.
     */
    val progress: Float?
        get() = (spent / budget.amount).coerceIn(0.0, 1.0).toFloat().takeIf { isResolved }

    /**
     * The three derived figures as amounts that **carry the mark this progress derived**,
     * so that no screen has to remember to attach it — and none of the three that show a
     * budget can disagree with the others.
     *
     * All three inherit [isApproximate], because all three are computed from [spent]: if
     * reaching the limit's currency took a rate, then what was spent, what is left and by
     * how much it was exceeded are equally approximate. And all three are `null` when
     * [isResolved] is false, which a surface renders through `formatOrUnresolved` — one
     * decision about what "no number" looks like, taken once.
     */
    val spentAmount: DisplayAmount?
        get() = DisplayAmount.magnitude(spent, budget.currency, isApproximate).takeIf { isResolved }

    val remainingAmount: DisplayAmount?
        get() = DisplayAmount.magnitude(remaining, budget.currency, isApproximate)
            .takeIf { isResolved }

    val exceededAmount: DisplayAmount?
        get() = DisplayAmount.magnitude(spent - budget.amount, budget.currency, isApproximate)
            .takeIf { isResolved }

    /**
     * The limit, which is **never** approximate: the user typed it, in a currency chosen
     * once and never re-denominated (design D13). It is here so the pair reads from one
     * place, not because it could ever carry a mark.
     */
    val limitAmount: DisplayAmount
        get() = DisplayAmount.magnitude(budget.amount, budget.currency, isApproximate = false)
}
