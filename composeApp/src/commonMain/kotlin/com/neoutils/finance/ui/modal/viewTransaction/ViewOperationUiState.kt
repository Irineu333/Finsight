package com.neoutils.finance.ui.modal.viewTransaction

import com.neoutils.finance.domain.model.Operation

data class ViewOperationUiState(
    val operation: Operation,
) {
    val transaction = operation.primaryTransaction
    val title = when (operation.kind) {
        Operation.Kind.TRANSFER -> "Transferência"
        else -> operation.label
    }
}
