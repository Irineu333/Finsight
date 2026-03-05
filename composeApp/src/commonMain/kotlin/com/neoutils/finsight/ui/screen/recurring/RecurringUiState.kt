package com.neoutils.finsight.ui.screen.recurring

import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.model.Transaction

enum class RecurringFilter {
    ALL, INCOME, EXPENSE
}

enum class RecurringStatusFilter {
    ACTIVE, INACTIVE, ALL
}

data class RecurringUiState(
    val recurring: List<Recurring> = emptyList(),
    val selectedFilter: RecurringFilter = RecurringFilter.ALL,
    val selectedStatusFilter: RecurringStatusFilter = RecurringStatusFilter.ACTIVE,
    val isLoading: Boolean = true,
) {
    val filteredRecurring: List<Recurring>
        get() = recurring
            .filter { recurring ->
                when (selectedFilter) {
                    RecurringFilter.ALL -> true
                    RecurringFilter.INCOME -> recurring.type == Transaction.Type.INCOME
                    RecurringFilter.EXPENSE -> recurring.type == Transaction.Type.EXPENSE
                }
            }
            .filter { recurring ->
                when (selectedStatusFilter) {
                    RecurringStatusFilter.ACTIVE -> recurring.isActive
                    RecurringStatusFilter.INACTIVE -> !recurring.isActive
                    RecurringStatusFilter.ALL -> true
                }
            }
            .sortedWith(compareByDescending<Recurring> { it.isActive }.thenBy { it.createdAt })
}
