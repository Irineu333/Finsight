package com.neoutils.finsight.ui.modal.viewTransaction

import com.neoutils.finsight.domain.model.Operation
import com.neoutils.finsight.ui.model.OperationPerspective

sealed interface ViewOperationUiState {

    data object Loading : ViewOperationUiState

    data object Error : ViewOperationUiState

    data class Content(
        val operation: Operation,
        val perspective: OperationPerspective? = null,
    ) : ViewOperationUiState {
        val transaction = perspective?.let { selectedPerspective ->
            selectedPerspective.resolve(operation = operation)
        } ?: operation.primaryTransaction
    }
}
