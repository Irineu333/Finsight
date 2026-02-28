package com.neoutils.finsight.domain.model

data class Budget(
    val id: Long = 0,
    val title: String,
    val categories: List<Category>,
    val iconCategoryId: Long,
    val amount: Double,
    val createdAt: Long,
) {
    val iconCategory: Category?
        get() = categories.find { it.id == iconCategoryId } ?: categories.firstOrNull()
}
