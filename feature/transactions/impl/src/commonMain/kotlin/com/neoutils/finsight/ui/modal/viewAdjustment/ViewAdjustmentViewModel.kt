package com.neoutils.finsight.ui.modal.viewAdjustment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.exception.DetailNotFoundException
import com.neoutils.finsight.domain.repository.IOperationRepository
import com.neoutils.finsight.extension.interceptAbsence
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn

class ViewAdjustmentViewModel(
    operationId: Long,
    operationRepository: IOperationRepository,
    private val crashlytics: Crashlytics,
) : ViewModel() {

    private val _events = Channel<ViewAdjustmentEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val uiState = operationRepository.observeOperationById(operationId)
        .interceptAbsence(
            onMissing = { crashlytics.recordException(DetailNotFoundException("Operation", operationId)) },
            onDisappeared = { _events.send(ViewAdjustmentEvent.Dismiss) },
        )
        .map { operation ->
            operation?.let { ViewAdjustmentUiState.Content(it) }
                ?: ViewAdjustmentUiState.Error
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ViewAdjustmentUiState.Loading,
        )
}
