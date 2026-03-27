package com.neoutils.finsight.ui.modal.viewTransaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.model.Operation
import com.neoutils.finsight.domain.repository.IOperationRepository
import com.neoutils.finsight.ui.model.OperationPerspective
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ViewOperationViewModel(
    operation: Operation,
    private val perspective: OperationPerspective? = null,
    operationRepository: IOperationRepository,
) : ViewModel() {

    private val operationFlow = flow {
        emit(operationRepository.getOperationById(operation.id) ?: operation)
    }

    private val _events = Channel<ViewOperationEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val uiState = operationFlow
        .map { currentOperation ->
            ViewOperationUiState(
                operation = currentOperation,
                perspective = perspective,
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ViewOperationUiState(
                operation = operation,
                perspective = perspective,
            )
        )

    fun onAction(action: ViewOperationAction) = viewModelScope.launch {
        when (action) {
            is ViewOperationAction.OpenRecurring -> {
                _events.send(
                    ViewOperationEvent.OpenRecurring(action.recurring)
                )
            }
        }
    }
}
