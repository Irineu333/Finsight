package com.neoutils.finsight.ui.modal.viewAdjustment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.repository.IOperationRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn

class ViewAdjustmentViewModel(
    operationId: Long,
    operationRepository: IOperationRepository,
) : ViewModel() {

    private var loadedOnce = false

    private val _events = Channel<ViewAdjustmentEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val uiState = operationRepository.observeOperationById(operationId)
        .map { operation ->
            when {
                operation != null -> {
                    loadedOnce = true
                    ViewAdjustmentUiState.Content(operation)
                }

                loadedOnce -> {
                    _events.send(ViewAdjustmentEvent.Dismiss)
                    ViewAdjustmentUiState.Loading
                }

                else -> ViewAdjustmentUiState.Error
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ViewAdjustmentUiState.Loading,
        )
}
