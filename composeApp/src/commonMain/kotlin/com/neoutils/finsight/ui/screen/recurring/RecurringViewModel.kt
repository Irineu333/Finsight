package com.neoutils.finsight.ui.screen.recurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.repository.IRecurringRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class RecurringViewModel(
    private val recurringRepository: IRecurringRepository,
) : ViewModel() {
    private val selectedFilter = MutableStateFlow(RecurringFilter.ALL)
    private val selectedStatusFilter = MutableStateFlow(RecurringStatusFilter.ACTIVE)

    val uiState = combine(
        recurringRepository.observeAllRecurring(),
        selectedFilter,
        selectedStatusFilter,
    ) { recurring, filter, statusFilter ->
        RecurringUiState(
            recurring = recurring,
            selectedFilter = filter,
            selectedStatusFilter = statusFilter,
            isLoading = false,
        )
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = RecurringUiState(),
        )

    fun onAction(action: RecurringAction) {
        when (action) {
            is RecurringAction.SelectFilter -> selectedFilter.value = action.filter
            is RecurringAction.SelectStatusFilter -> selectedStatusFilter.value = action.filter
        }
    }
}
