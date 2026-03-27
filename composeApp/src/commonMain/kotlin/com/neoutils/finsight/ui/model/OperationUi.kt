package com.neoutils.finsight.ui.model

import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.Operation
import com.neoutils.finsight.domain.model.Transaction
import kotlinx.datetime.LocalDate

data class OperationUi(
    val operation: Operation,
    val perspective: OperationPerspective,
) {
    val id: Long
        get() = operation.id

    val kind: Operation.Kind
        get() = operation.kind

    val recurring = operation.recurring

    val transaction: Transaction by lazy {
        requireNotNull(
            perspective.resolve(
                operation = operation,
            )
        ) {
            "Operation ${operation.id} does not match perspective $perspective"
        }
    }

    val displayType: Transaction.Type
        get() = when (kind) {
            Operation.Kind.PAYMENT -> Transaction.Type.EXPENSE
            else -> transaction.type
        }

    val displayAmount: Double
        get() = transaction.amount

    val displayDate: LocalDate
        get() = transaction.date

    val displayTarget: Transaction.Target
        get() = transaction.target

    val displayCategory: Category?
        get() = operation.category ?: transaction.category
}
