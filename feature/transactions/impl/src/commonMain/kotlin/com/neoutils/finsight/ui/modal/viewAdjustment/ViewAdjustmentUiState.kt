package com.neoutils.finsight.ui.modal.viewAdjustment

import com.neoutils.finsight.domain.model.Operation

sealed interface ViewAdjustmentUiState {

    data object Loading : ViewAdjustmentUiState

    data class Content(
        val operation: Operation,
    ) : ViewAdjustmentUiState {
        val transaction = operation.primaryTransaction
    }
}
