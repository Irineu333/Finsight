package com.neoutils.finsight.ui.modal.viewTransaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.repository.IOperationRepository
import com.neoutils.finsight.ui.model.OperationPerspective
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ViewOperationViewModel(
    operationId: Long,
    private val perspective: OperationPerspective? = null,
    operationRepository: IOperationRepository,
) : ViewModel() {

    private val _events = Channel<ViewOperationEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val uiState = operationRepository.observeOperationById(operationId)
        .onEach { if (it == null) _events.send(ViewOperationEvent.Dismiss) }
        .filterNotNull()
        .map { ViewOperationUiState.Content(it, perspective) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ViewOperationUiState.Loading,
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
