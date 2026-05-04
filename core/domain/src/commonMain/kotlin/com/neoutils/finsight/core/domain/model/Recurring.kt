package com.neoutils.finsight.core.domain.model

data class Recurring(
    val id: Long = 0,
    val type: Type,
    val amount: Double,
    val title: String?,
    val dayOfMonth: Int,
    val categoryId: Long? = null,
    val accountId: Long? = null,
    val creditCardId: Long? = null,
    val createdAt: Long,
    val isActive: Boolean = true,
) {
    val label get() = title?.takeIf { it.isNotBlank() } ?: "Untitled"

    enum class Type {
        INCOME,
        EXPENSE;

        val isIncome: Boolean get() = this == INCOME
        val isExpense: Boolean get() = this == EXPENSE
    }
}
