package com.neoutils.finsight.domain.model

data class Recurring(
    val id: Long = 0,
    val type: Recurring.Type,
    val amount: Double,
    val title: String?,
    val dayOfMonth: Int,
    val category: Category?,
    val account: Account?,
    val creditCard: CreditCard?,
    val createdAt: Long,
    val isActive: Boolean = true,
) {
    val label get() = title?.takeIf { it.isNotBlank() } ?: category?.name?.takeIf { it.isNotBlank() } ?: "Untitled"

    enum class Type {
        INCOME,
        EXPENSE;

        val isIncome: Boolean get() = this == INCOME
        val isExpense: Boolean get() = this == EXPENSE
    }
}
