package com.neoutils.finsight.ui.screen.recurring

import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.model.Transaction

enum class RecurringFilter {
    ALL, INCOME, EXPENSE
}

enum class RecurringStatusFilter {
    ACTIVE, INACTIVE, ALL
}

sealed class RecurringUiState {

    abstract val selectedFilter: RecurringFilter
    abstract val selectedStatusFilter: RecurringStatusFilter

    data class Loading(
        override val selectedFilter: RecurringFilter = RecurringFilter.ALL,
        override val selectedStatusFilter: RecurringStatusFilter = RecurringStatusFilter.ACTIVE,
    ) : RecurringUiState()

    data class Empty(
        override val selectedFilter: RecurringFilter,
        override val selectedStatusFilter: RecurringStatusFilter,
    ) : RecurringUiState()

    data class Content(
        val filteredRecurring: List<Recurring>,
        override val selectedFilter: RecurringFilter,
        override val selectedStatusFilter: RecurringStatusFilter,
    ) : RecurringUiState()
}