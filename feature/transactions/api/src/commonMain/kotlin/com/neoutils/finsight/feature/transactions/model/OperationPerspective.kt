package com.neoutils.finsight.feature.transactions.model

sealed class OperationPerspective {

    data object Target : OperationPerspective()

    data class Account(
        val accountId: Long,
    ) : OperationPerspective()

    data class Card(
        val creditCardId: Long,
        val invoiceId: Long? = null,
    ) : OperationPerspective()

    fun resolve(
        operation: Operation,
    ): Transaction? {
        return when (this) {
            Target -> when (operation.kind) {
                Operation.Kind.TRANSACTION -> operation.transactions.firstOrNull()

                Operation.Kind.TRANSFER -> operation.transactions.firstOrNull { transaction ->
                    transaction.type == Transaction.Type.EXPENSE &&
                            transaction.target == Transaction.Target.ACCOUNT
                }

                Operation.Kind.PAYMENT -> operation.transactions.firstOrNull { transaction ->
                    transaction.target == Transaction.Target.CREDIT_CARD
                }
            }

            is Account -> {
                operation.transactions.firstOrNull { transaction ->
                    transaction.target.isAccount && transaction.accountId == accountId
                }
            }

            is Card -> {
                operation.transactions.firstOrNull { transaction ->
                    transaction.target.isCreditCard &&
                            transaction.creditCardId == creditCardId &&
                            (invoiceId == null || transaction.invoiceId == invoiceId)
                }
            }
        }
    }
}
