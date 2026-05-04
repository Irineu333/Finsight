package com.neoutils.finsight.feature.transactions.modal.viewTransaction

import com.neoutils.finsight.core.domain.model.Account
import com.neoutils.finsight.core.domain.model.Category
import com.neoutils.finsight.core.domain.model.CreditCard
import com.neoutils.finsight.core.domain.model.Invoice
import com.neoutils.finsight.core.domain.model.Operation
import com.neoutils.finsight.core.domain.model.OperationPerspective
import com.neoutils.finsight.core.domain.model.Transaction

data class ViewOperationUiState(
    val operation: Operation,
    val perspective: OperationPerspective? = null,
    val category: Category? = null,
    val account: Account? = null,
    val creditCard: CreditCard? = null,
    val invoice: Invoice? = null,
    val sourceAccount: Account? = null,
    val destinationAccount: Account? = null,
) {
    val transaction: Transaction = perspective?.let { selectedPerspective ->
        selectedPerspective.resolve(operation = operation)
    } ?: operation.primaryTransaction
}
