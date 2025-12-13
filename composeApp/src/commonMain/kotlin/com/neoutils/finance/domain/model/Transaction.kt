package com.neoutils.finance.domain.model

import kotlinx.datetime.LocalDate

data class Transaction(
    val id: Long = 0,
    val type: Type,
    val amount: Double,
    val title: String?,
    val date: LocalDate,
    val category: Category? = null,
    val target: Target = Target.ACCOUNT,
    val creditCardId: Long? = null
) {
    enum class Type {
        EXPENSE,
        INCOME,
        ADJUSTMENT,
        INVOICE_PAYMENT;

        val isExpense: Boolean get() = this == EXPENSE
        val isIncome: Boolean get() = this == INCOME
        val isAdjustment: Boolean get() = this == ADJUSTMENT
        val isInvoicePayment: Boolean get() = this == INVOICE_PAYMENT
    }

    enum class Target {
        ACCOUNT,
        CREDIT_CARD,
        INVOICE_PAYMENT;

        val isAccount: Boolean
            get() = when (this) {
                ACCOUNT,
                INVOICE_PAYMENT -> true

                CREDIT_CARD -> false
            }
        val isCreditCard: Boolean
            get() = when (this) {
                INVOICE_PAYMENT,
                CREDIT_CARD -> true

                ACCOUNT -> false
            }
    }
}
