package com.neoutils.finsight.feature.transactions.modal.viewAdjustment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.core.domain.model.Operation
import com.neoutils.finsight.feature.transactions.repository.IOperationRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class ViewAdjustmentViewModel(
    operation: Operation,
    operationRepository: IOperationRepository,
) : ViewModel() {

    private val operationFlow = flow {
        emit(operationRepository.getOperationById(operation.id) ?: operation)
    }

    val uiState = operationFlow
        .map { ViewAdjustmentUiState(operation = it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ViewAdjustmentUiState(
                operation = operation
            )
        )
}

