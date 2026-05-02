package com.neoutils.finsight.feature.budgets.model
import com.neoutils.finsight.core.domain.model.Category

data class Budget(
    val id: Long = 0,
    val title: String,
    val categories: List<Category>,
    val iconKey: String,
    val amount: Double,
    val limitType: LimitType = LimitType.FIXED,
    val percentage: Double? = null,
    val recurringId: Long? = null,
    val createdAt: Long,
)