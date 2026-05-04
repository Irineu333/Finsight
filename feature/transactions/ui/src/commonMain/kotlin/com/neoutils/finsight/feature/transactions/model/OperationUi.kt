package com.neoutils.finsight.feature.transactions.model

import com.neoutils.finsight.feature.accounts.model.Account
import com.neoutils.finsight.feature.categories.model.Category
import com.neoutils.finsight.feature.creditCards.model.CreditCard
import com.neoutils.finsight.feature.transactions.model.Operation
import com.neoutils.finsight.feature.transactions.model.OperationPerspective
import com.neoutils.finsight.feature.transactions.model.Transaction
import kotlinx.datetime.LocalDate

data class OperationUi(
    val operation: Operation,
    val perspective: OperationPerspective,
    val category: Category? = null,
    val sourceAccount: Account? = null,
    val targetCreditCard: CreditCard? = null,
    val targetInvoice: com.neoutils.finsight.feature.creditCards.model.InvoiceUi? = null,
    val transactions: List<TransactionUi> = emptyList(),
) {
    val id: Long get() = operation.id
    val kind: Operation.Kind get() = operation.kind
    val recurring = operation.recurring
    val installment = operation.installment

    val resolvedTransaction: TransactionUi by lazy {
        val target = perspective.resolve(operation)
        transactions.firstOrNull { it.transaction.id == target?.id }
            ?: transactions.first()
    }

    val displayLabel: String
        get() = operation.title?.takeIf { it.isNotBlank() }
            ?: category?.name?.takeIf { it.isNotBlank() }
            ?: "Untitled"

    val displayType: Transaction.Type
        get() = when (kind) {
            Operation.Kind.PAYMENT -> Transaction.Type.EXPENSE
            else -> resolvedTransaction.type
        }

    val displayAmount: Double
        get() = resolvedTransaction.amount

    val displayDate: LocalDate
        get() = resolvedTransaction.date

    val displayTarget: Transaction.Target
        get() = resolvedTransaction.target

    val displayCategory: Category?
        get() = category ?: resolvedTransaction.category
}
