package com.neoutils.finsight.domain.model
import com.neoutils.finsight.core.domain.model.Category

data class CategorySpending(
    val category: Category,
    val amount: Double,
    val percentage: Double
)
