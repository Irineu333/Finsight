package com.neoutils.finsight.domain.model

import com.neoutils.finsight.ui.icons.CategoryLazyIcon

data class Goal(
    val id: Long = 0,
    val title: String,
    val categories: List<Category>,
    val iconKey: String,
    val amount: Double,
    val createdAt: Long,
) {
    val icon: CategoryLazyIcon
        get() = CategoryLazyIcon(iconKey)
}
