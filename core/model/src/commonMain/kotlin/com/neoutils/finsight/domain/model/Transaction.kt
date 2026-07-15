package com.neoutils.finsight.domain.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

data class Transaction(
    val id: Long = 0,
    val operationId: Long? = null,
    val type: Type,
    val amount: Double,
    val title: String?,
    val date: LocalDate,
    val category: Category? = null,
    val creditCard: CreditCard? = null,
    val invoice: Invoice? = null,
    val account: Account? = null,
) {
    // Derived from the leg's account: a card leg targets the card, otherwise an account.
    val target: Target
        get() = if (creditCard != null) Target.CREDIT_CARD else Target.ACCOUNT

    @Serializable
    enum class Type {
        EXPENSE,
        INCOME,
        ADJUSTMENT;

        val isExpense: Boolean get() = this == EXPENSE
        val isIncome: Boolean get() = this == INCOME
        val isAdjustment: Boolean get() = this == ADJUSTMENT
    }

    @Serializable
    enum class Target {
        ACCOUNT,
        CREDIT_CARD;

        val isAccount: Boolean
            get() = this == ACCOUNT
        val isCreditCard: Boolean
            get() = this == CREDIT_CARD
    }

    val isInvoicePayment: Boolean get() = invoice != null
}
