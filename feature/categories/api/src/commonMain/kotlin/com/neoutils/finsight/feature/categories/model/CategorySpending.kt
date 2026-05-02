package com.neoutils.finsight.feature.categories.model
import com.neoutils.finsight.core.domain.model.Category

data class CategorySpending(
    val category: Category,
    val amount: Double,
    val percentage: Double
)
