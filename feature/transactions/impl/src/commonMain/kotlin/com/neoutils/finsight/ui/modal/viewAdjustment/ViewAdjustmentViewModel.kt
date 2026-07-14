package com.neoutils.finsight.ui.modal.viewAdjustment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.exception.DetailNotFoundException
import com.neoutils.finsight.domain.repository.IOperationRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.withIndex

class ViewAdjustmentViewModel(
    operationId: Long,
    operationRepository: IOperationRepository,
    private val crashlytics: Crashlytics,
) : ViewModel() {

    private val _events = Channel<ViewAdjustmentEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val uiState = operationRepository.observeOperationById(operationId)
        .distinctUntilChanged()
        .withIndex()
        .onEach { (index, operation) ->
            when {
                operation != null -> Unit
                index == 0 -> crashlytics.recordException(DetailNotFoundException("Operation", operationId))
                else -> _events.send(ViewAdjustmentEvent.Dismiss)
            }
        }
        .filter { (index, operation) -> operation != null || index == 0 }
        .map { (_, operation) ->
            operation?.let { ViewAdjustmentUiState.Content(it) }
                ?: ViewAdjustmentUiState.Error
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ViewAdjustmentUiState.Loading,
        )
}
