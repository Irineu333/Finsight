package com.neoutils.finsight.ui.modal.viewRecurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.exception.DetailNotFoundException
import com.neoutils.finsight.domain.repository.IRecurringRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.withIndex

class ViewRecurringViewModel(
    recurringId: Long,
    recurringRepository: IRecurringRepository,
    private val crashlytics: Crashlytics,
) : ViewModel() {

    private val _events = Channel<ViewRecurringEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val uiState = recurringRepository.observeRecurringById(recurringId)
        .distinctUntilChanged()
        .withIndex()
        .onEach { (index, recurring) ->
            when {
                recurring != null -> Unit
                index == 0 -> crashlytics.recordException(DetailNotFoundException("Recurring", recurringId))
                else -> _events.send(ViewRecurringEvent.Dismiss)
            }
        }
        .filter { (index, recurring) -> recurring != null || index == 0 }
        .map { (_, recurring) ->
            recurring?.let { ViewRecurringUiState.Content(it) }
                ?: ViewRecurringUiState.Error
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ViewRecurringUiState.Loading,
        )
}
