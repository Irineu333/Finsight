package com.neoutils.finsight.feature.transactions.modal.viewAdjustment

import com.neoutils.finsight.feature.transactions.model.Operation

data class ViewAdjustmentUiState(
    val operation: Operation,
) {
    val transaction = operation.primaryTransaction
}