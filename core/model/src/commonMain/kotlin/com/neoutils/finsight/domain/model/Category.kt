package com.neoutils.finsight.domain.model

import com.neoutils.finsight.ui.icons.CategoryLazyIcon

data class Category(
    val id: Long = 0,
    val name: String,
    val icon: CategoryLazyIcon,
    val type: Type,
    val createdAt: Long,
    // The chart-of-accounts row (INCOME/EXPENSE) this category projects onto.
    val accountId: Long? = null,
) {
    enum class Type {
        INCOME,
        EXPENSE;

        val isIncome: Boolean get() = this == INCOME
        val isExpense: Boolean get() = this == EXPENSE
    }
}
