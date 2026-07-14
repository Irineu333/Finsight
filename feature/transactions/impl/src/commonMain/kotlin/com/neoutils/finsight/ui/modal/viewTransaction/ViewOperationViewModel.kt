package com.neoutils.finsight.ui.modal.viewTransaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.exception.DetailNotFoundException
import com.neoutils.finsight.domain.repository.IOperationRepository
import com.neoutils.finsight.ui.model.OperationPerspective
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.withIndex
import kotlinx.coroutines.launch

class ViewOperationViewModel(
    operationId: Long,
    private val perspective: OperationPerspective? = null,
    operationRepository: IOperationRepository,
    private val crashlytics: Crashlytics,
) : ViewModel() {

    private val _events = Channel<ViewOperationEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val uiState = operationRepository.observeOperationById(operationId)
        .distinctUntilChanged()
        .withIndex()
        .onEach { (index, operation) ->
            when {
                operation != null -> Unit
                index == 0 -> crashlytics.recordException(DetailNotFoundException("Operation", operationId))
                else -> _events.send(ViewOperationEvent.Dismiss)
            }
        }
        .filter { (index, operation) -> operation != null || index == 0 }
        .map { (_, operation) ->
            operation?.let {
                ViewOperationUiState.Content(it, perspective)
            } ?: ViewOperationUiState.Error
        }
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
