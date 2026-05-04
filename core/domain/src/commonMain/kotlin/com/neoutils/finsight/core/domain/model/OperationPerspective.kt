package com.neoutils.finsight.core.domain.model

sealed class OperationPerspective {
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
