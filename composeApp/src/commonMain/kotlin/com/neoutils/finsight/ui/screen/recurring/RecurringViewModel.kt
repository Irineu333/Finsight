package com.neoutils.finsight.ui.screen.recurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.repository.IRecurringRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
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
        val filteredRecurring = recurring
            .filter { r ->
                when (filter) {
                    RecurringFilter.ALL -> true
                    RecurringFilter.INCOME -> r.type == Recurring.Type.INCOME
                    RecurringFilter.EXPENSE -> r.type == Recurring.Type.EXPENSE
                }
            }
            .filter { r ->
                when (statusFilter) {
                    RecurringStatusFilter.ACTIVE -> r.isActive
                    RecurringStatusFilter.INACTIVE -> !r.isActive
                    RecurringStatusFilter.ALL -> true
                }
            }
            .sortedWith(compareByDescending<Recurring> { it.isActive }.thenBy { it.createdAt })

        if (filteredRecurring.isEmpty()) {
            RecurringUiState.Empty(selectedFilter = filter, selectedStatusFilter = statusFilter)
        } else {
            RecurringUiState.Content(
                filteredRecurring = filteredRecurring,
                selectedFilter = filter,
                selectedStatusFilter = statusFilter,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = RecurringUiState.Loading(),
    )

    fun onAction(action: RecurringAction) {
        when (action) {
            is RecurringAction.SelectFilter -> selectedFilter.value = action.filter
            is RecurringAction.SelectStatusFilter -> selectedStatusFilter.value = action.filter
        }
    }
}
