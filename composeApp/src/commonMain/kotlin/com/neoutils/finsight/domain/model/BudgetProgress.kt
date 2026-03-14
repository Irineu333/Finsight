package com.neoutils.finsight.domain.model

data class BudgetProgress(
    val budget: Budget,
    val spent: Double,
    val recurringLabel: String? = null,
) {
    val progress: Float get() = (spent / budget.amount).coerceIn(0.0, 1.0).toFloat()
    val remaining: Double get() = (budget.amount - spent).coerceAtLeast(0.0)
    val isExceeded: Boolean get() = spent > budget.amount
}
