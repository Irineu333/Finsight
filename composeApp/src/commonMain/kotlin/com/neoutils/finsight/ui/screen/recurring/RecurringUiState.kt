package com.neoutils.finsight.ui.screen.recurring

import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.model.Transaction

enum class RecurringFilter {
    ALL, INCOME, EXPENSE
}

data class RecurringUiState(
    val recurring: List<Recurring> = emptyList(),
    val selectedFilter: RecurringFilter = RecurringFilter.ALL,
) {
    val filteredRecurring: List<Recurring>
        get() = recurring.filter { recurring ->
            when (selectedFilter) {
                RecurringFilter.ALL -> true
                RecurringFilter.INCOME -> recurring.type == Transaction.Type.INCOME
                RecurringFilter.EXPENSE -> recurring.type == Transaction.Type.EXPENSE
            }
        }
}
