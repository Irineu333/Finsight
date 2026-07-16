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
    // Derived from the leg: an account leg targets the account (an invoice-payment
    // leg carries the card reference too, so `account` takes precedence); only a
    // pure card leg (no account) targets the card.
    val target: Target
        get() = if (account != null) Target.ACCOUNT else Target.CREDIT_CARD

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
