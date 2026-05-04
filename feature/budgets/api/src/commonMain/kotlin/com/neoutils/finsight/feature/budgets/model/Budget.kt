package com.neoutils.finsight.feature.budgets.model

data class Budget(
    val id: Long = 0,
    val title: String,
    val categoryIds: List<Long>,
    val iconKey: String,
    val amount: Double,
    val limitType: LimitType = LimitType.FIXED,
    val percentage: Double? = null,
    val recurringId: Long? = null,
    val createdAt: Long,
)