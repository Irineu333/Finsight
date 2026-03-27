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

    companion object {
        fun resolveTransaction(
            operation: Operation,
            perspective: OperationPerspective,
        ): Transaction? {
            return when (perspective) {
                is Account -> operation.transactions.firstOrNull { transaction ->
                    transaction.account?.id == perspective.accountId
                }

                is Card -> operation.transactions.firstOrNull { transaction ->
                    transaction.creditCard?.id == perspective.creditCardId ||
                        (perspective.invoiceId != null && transaction.invoice?.id == perspective.invoiceId)
                }
            }
        }
    }
}
