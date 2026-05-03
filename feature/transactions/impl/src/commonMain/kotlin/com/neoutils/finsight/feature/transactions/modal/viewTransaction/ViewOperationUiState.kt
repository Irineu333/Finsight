package com.neoutils.finsight.feature.transactions.modal.viewTransaction

import com.neoutils.finsight.core.domain.model.Operation
import com.neoutils.finsight.core.domain.model.OperationPerspective
data class ViewOperationUiState(
    val operation: Operation,
    val perspective: OperationPerspective? = null,
) {
    val transaction = perspective?.let { selectedPerspective ->
        selectedPerspective.resolve(operation = operation)
    } ?: operation.primaryTransaction
}
