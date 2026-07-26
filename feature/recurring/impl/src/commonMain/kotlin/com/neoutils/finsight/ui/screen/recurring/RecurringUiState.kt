package com.neoutils.finsight.ui.screen.recurring

import com.neoutils.finsight.domain.model.Recurring

sealed class RecurringUiState {

    abstract val filter: RecurringFilter

    data class Loading(
        override val filter: RecurringFilter = RecurringFilter.ACTIVE,
    ) : RecurringUiState()

    /**
     * The database holds no recurring at all — the only case that earns the big CTA
     * empty-state. A filter that merely happens to be empty is [Content] with an
     * empty list. [filter] survives so the FAB is still shown and knows its context.
     */
    data class Empty(
        override val filter: RecurringFilter,
    ) : RecurringUiState()

    data class Content(
        val filteredRecurring: List<Recurring>,
        override val filter: RecurringFilter,
    ) : RecurringUiState()
}
