package com.neoutils.finsight.ui.modal.viewAdjustment

import com.neoutils.finsight.domain.model.Operation

data class ViewAdjustmentUiState(
    val operation: Operation,
) {
    val transaction = operation.primaryTransaction
}