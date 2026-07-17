package com.neoutils.finsight.domain.model

import kotlinx.datetime.LocalDate

data class Operation(
    val id: Long = 0,
    val title: String?,
    val date: LocalDate,
    val recurring: OperationRecurring? = null,
    val category: Category? = null,
    val sourceAccount: Account? = null,
    val targetCreditCard: CreditCard? = null,
    val targetInvoice: Invoice? = null,
    val installment: OperationInstallment? = null,
    val transactions: List<Transaction>,
    // The balanced double-entry legs of this operation, each hydrated with its
    // account. Empty only for operations built outside the ledger read path.
    val entries: List<Entry> = emptyList(),
) {
    val label get() = title?.takeIf { it.isNotBlank() } ?: category?.name?.takeIf { it.isNotBlank() } ?: "Untitled"

    // Derived from the legs, never persisted: two accounts = transfer, an account
    // and a card = payment, otherwise a single-leg transaction.
    val kind: Kind
        get() = when {
            transactions.size >= 2 && transactions.any { it.target == Transaction.Target.CREDIT_CARD } -> Kind.PAYMENT
            transactions.size >= 2 -> Kind.TRANSFER
            else -> Kind.TRANSACTION
        }

    val accountTransaction: Transaction?
        get() = transactions.firstOrNull { it.target == Transaction.Target.ACCOUNT }

    val creditCardTransaction: Transaction?
        get() = transactions.firstOrNull { it.target == Transaction.Target.CREDIT_CARD }

    val primaryTransaction: Transaction
        get() = accountTransaction ?: transactions.first()

    val type: Transaction.Type
        get() = if (kind == Kind.PAYMENT) Transaction.Type.EXPENSE else primaryTransaction.type

    val creditCardType: Transaction.Type
        get() = if (kind == Kind.PAYMENT) Transaction.Type.INCOME else primaryTransaction.type

    val target: Transaction.Target
        get() = primaryTransaction.target

    val amount: Double
        get() = primaryTransaction.amount

    val isEditable: Boolean
        get() = transactions.size == 1

    val hasInstallment: Boolean
        get() = installment != null

    enum class Kind {
        TRANSACTION,
        PAYMENT,
        TRANSFER,
    }
}
