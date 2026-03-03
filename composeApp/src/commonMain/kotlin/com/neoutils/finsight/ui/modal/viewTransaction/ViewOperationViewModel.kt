package com.neoutils.finsight.ui.modal.viewTransaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.model.Operation
import com.neoutils.finsight.domain.repository.IOperationRepository
import com.neoutils.finsight.domain.repository.IRecurringRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ViewOperationViewModel(
    operation: Operation,
    operationRepository: IOperationRepository,
    recurringRepository: IRecurringRepository,
) : ViewModel() {

    private val operationFlow = flow {
        emit(operationRepository.getOperationById(operation.id) ?: operation)
    }

    private val recurringList = recurringRepository.observeAllRecurring()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )

    private val _event = MutableSharedFlow<ViewOperationEvent>()
    val event = _event.asSharedFlow()

    val uiState = operationFlow
        .map { currentOperation ->
            ViewOperationUiState(
                operation = currentOperation,
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ViewOperationUiState(
                operation = operation
            )
        )

    fun onAction(action: ViewOperationAction) {
        when (action) {
            ViewOperationAction.OpenRecurring -> {
                val recurringId = uiState.value.operation.recurring?.id ?: return
                val recurring = recurringList.value.firstOrNull { it.id == recurringId } ?: return

                viewModelScope.launch {
                    _event.emit(ViewOperationEvent.OpenRecurring(recurring))
                }
            }
        }
    }
}
