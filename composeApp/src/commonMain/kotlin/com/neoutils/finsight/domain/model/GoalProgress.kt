package com.neoutils.finsight.domain.model

data class GoalProgress(
    val goal: Goal,
    val earned: Double,
) {
    val progress: Float get() = (earned / goal.amount).coerceIn(0.0, 1.0).toFloat()
    val remaining: Double get() = (goal.amount - earned).coerceAtLeast(0.0)
    val exceeded: Double get() = (earned - goal.amount).coerceAtLeast(0.0)
    val isReached: Boolean get() = earned >= goal.amount
}
