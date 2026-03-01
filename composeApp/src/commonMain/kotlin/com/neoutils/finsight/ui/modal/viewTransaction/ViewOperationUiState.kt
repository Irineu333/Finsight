package com.neoutils.finsight.ui.modal.viewTransaction

import com.neoutils.finsight.domain.model.Operation

data class ViewOperationUiState(
    val operation: Operation,
) {
    val transaction = operation.primaryTransaction
}
