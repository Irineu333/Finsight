package com.neoutils.finsight.domain.model

data class Category(
    val id: Long = 0,
    val name: String,
    val iconKey: String,
    val type: Type,
    val createdAt: Long
) {
    enum class Type {
        INCOME,
        EXPENSE;

        val isIncome: Boolean get() = this == INCOME
        val isExpense: Boolean get() = this == EXPENSE
    }
}
