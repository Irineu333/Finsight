package com.neoutils.finsight.ui.screen.recurring

import kotlinx.datetime.YearMonth

sealed class RecurringAction {
    data class SelectFilter(val filter: RecurringFilter) : RecurringAction()

    /** The month of the whole screen — the summary and the cycles the list shows. */
    data class SelectMonth(val yearMonth: YearMonth) : RecurringAction()
}
