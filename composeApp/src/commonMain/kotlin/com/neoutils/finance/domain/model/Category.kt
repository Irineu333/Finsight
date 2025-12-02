package com.neoutils.finance.domain.model

data class Category(
    val id: Long = 0,
    val name: String,
    val key: String,
    val type: CategoryType,
    val createdAt: Long
) {
    enum class CategoryType {
        INCOME,
        EXPENSE;

        val isIncome: Boolean get() = this == INCOME
        val isExpense: Boolean get() = this == EXPENSE
    }
}
