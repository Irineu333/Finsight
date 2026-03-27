package com.neoutils.finsight.ui.model

import com.neoutils.finsight.domain.model.Operation
import com.neoutils.finsight.domain.model.Transaction

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
                    transaction.target.isAccount && transaction.account?.id == accountId
                }
            }

            is Card -> {
                operation.transactions.firstOrNull { transaction ->
                    transaction.target.isCreditCard &&
                            transaction.creditCard?.id == creditCardId &&
                            (invoiceId == null || transaction.invoice?.id == invoiceId)
                }
            }
        }
    }
}
