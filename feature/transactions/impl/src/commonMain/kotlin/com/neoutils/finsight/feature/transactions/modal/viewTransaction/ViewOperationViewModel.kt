package com.neoutils.finsight.feature.transactions.modal.viewTransaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.core.domain.model.Operation
import com.neoutils.finsight.feature.transactions.repository.IOperationRepository
import com.neoutils.finsight.feature.recurring.repository.IRecurringRepository
import com.neoutils.finsight.core.domain.model.OperationPerspective
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ViewOperationViewModel(
    operation: Operation,
    private val perspective: OperationPerspective? = null,
    operationRepository: IOperationRepository,
    private val recurringRepository: IRecurringRepository,
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
                val recurring = recurringRepository.observeAllRecurring().first()
                    .firstOrNull { it.id == action.recurringId }
                    ?: return@launch
                _events.send(ViewOperationEvent.OpenRecurring(recurring))
            }
        }
    }
}
