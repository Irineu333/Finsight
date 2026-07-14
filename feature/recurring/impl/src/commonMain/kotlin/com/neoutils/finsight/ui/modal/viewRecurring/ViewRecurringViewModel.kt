package com.neoutils.finsight.ui.modal.viewRecurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.repository.IRecurringRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn

class ViewRecurringViewModel(
    recurringId: Long,
    recurringRepository: IRecurringRepository,
) : ViewModel() {

    private val _events = Channel<ViewRecurringEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val uiState = recurringRepository.observeRecurringById(recurringId)
        .onEach { if (it == null) _events.send(ViewRecurringEvent.Dismiss) }
        .filterNotNull()
        .map { ViewRecurringUiState.Content(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ViewRecurringUiState.Loading,
        )
}
