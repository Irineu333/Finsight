package com.neoutils.finsight.feature.transactions.model

import kotlinx.datetime.LocalDate
import com.neoutils.finsight.core.domain.model.Account
import com.neoutils.finsight.core.domain.model.Category
import com.neoutils.finsight.core.domain.model.CreditCard
import com.neoutils.finsight.core.domain.model.Invoice
import com.neoutils.finsight.feature.transactions.model.Transaction
import com.neoutils.finsight.feature.transactions.model.Operation
import com.neoutils.finsight.feature.transactions.model.OperationInstallment
import com.neoutils.finsight.feature.transactions.model.OperationRecurring

data class Operation(
    val id: Long = 0,
    val kind: Kind,
    val title: String?,
    val date: LocalDate,
    val recurring: OperationRecurring? = null,
    val category: Category? = null,
    val sourceAccount: Account? = null,
    val targetCreditCard: CreditCard? = null,
    val targetInvoice: Invoice? = null,
    val installment: OperationInstallment? = null,
    val transactions: List<Transaction>,
) {
    val label get() = title?.takeIf { it.isNotBlank() } ?: category?.name?.takeIf { it.isNotBlank() } ?: "Untitled"

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