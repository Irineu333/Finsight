package com.neoutils.finsight.feature.transactions.modal.viewTransaction

import com.neoutils.finsight.feature.transactions.model.Operation
import com.neoutils.finsight.feature.transactions.model.OperationPerspective
data class ViewOperationUiState(
    val operation: Operation,
    val perspective: OperationPerspective? = null,
) {
    val transaction = perspective?.let { selectedPerspective ->
        selectedPerspective.resolve(operation = operation)
    } ?: operation.primaryTransaction
}
