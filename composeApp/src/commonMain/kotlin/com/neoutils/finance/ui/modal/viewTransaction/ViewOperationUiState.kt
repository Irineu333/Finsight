package com.neoutils.finance.ui.modal.viewTransaction

import com.neoutils.finance.domain.model.Operation

data class ViewOperationUiState(
    val operation: Operation,
) {
    val transaction = operation.primaryTransaction
    val title = operation.label
}
