package com.neoutils.finsight.ui.modal.viewTransaction

import com.neoutils.finsight.domain.model.Operation
import com.neoutils.finsight.ui.model.OperationPerspective

data class ViewOperationUiState(
    val operation: Operation,
    val perspective: OperationPerspective? = null,
) {
    val transaction = perspective?.let { selectedPerspective ->
        selectedPerspective.resolve(operation = operation)
    } ?: operation.primaryTransaction
}
