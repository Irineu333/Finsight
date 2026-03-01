package com.neoutils.finsight.domain.model

import kotlinx.datetime.LocalDate

data class Operation(
    val id: Long = 0,
    val kind: Kind,
    val title: String?,
    val date: LocalDate,
    val category: Category? = null,
    val sourceAccount: Account? = null,
    val targetCreditCard: CreditCard? = null,
    val targetInvoice: Invoice? = null,
    val installment: Installment? = null,
    val transactions: List<Transaction>,
) {
    enum class Kind {
        TRANSACTION,
        PAYMENT,
        TRANSFER,
    }

    val accountTransaction: Transaction?
        get() = transactions.firstOrNull { it.target == Transaction.Target.ACCOUNT }

    val creditCardTransaction: Transaction?
        get() = transactions.firstOrNull { it.target == Transaction.Target.CREDIT_CARD }

    val primaryTransaction: Transaction
        get() = accountTransaction ?: transactions.first()

    val type: Transaction.Type
        get() = if (kind == Kind.PAYMENT) Transaction.Type.EXPENSE else primaryTransaction.type

    val target: Transaction.Target
        get() = primaryTransaction.target

    val amount: Double
        get() = primaryTransaction.amount

    val label get() = checkNotNull(title ?: category?.name ?: "Unnamed")

    val isEditable: Boolean
        get() = transactions.size == 1

    val hasInstallment: Boolean
        get() = installment != null
}
