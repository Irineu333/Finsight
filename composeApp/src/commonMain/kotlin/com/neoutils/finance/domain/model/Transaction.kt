package com.neoutils.finance.domain.model

import kotlinx.datetime.LocalDate

data class Transaction(
    val id: Long = 0,
    val type: Type,
    val amount: Double,
    val title: String?,
    val date: LocalDate,
    val category: Category? = null,
    val target: Target = Target.ACCOUNT
) {
    enum class Type {
        EXPENSE,
        INCOME,
        ADJUSTMENT;

        val isExpense: Boolean get() = this == EXPENSE
        val isIncome: Boolean get() = this == INCOME
        val isAdjustment: Boolean get() = this == ADJUSTMENT
    }

    enum class Target {
        ACCOUNT,
        CREDIT_CARD;

        val isAccount: Boolean get() = this == ACCOUNT
        val isCreditCard: Boolean get() = this == CREDIT_CARD
    }
}
