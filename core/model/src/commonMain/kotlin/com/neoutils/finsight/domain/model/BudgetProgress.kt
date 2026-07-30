package com.neoutils.finsight.domain.model

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
}
