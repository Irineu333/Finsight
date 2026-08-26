package com.neoutils.finsight.ui.screen.recurring

import kotlinx.datetime.YearMonth

sealed class RecurringAction {
    data class SelectFilter(val filter: RecurringFilter) : RecurringAction()

    /** The month of the summary card. It does not reach the list (design D11). */
    data class SelectMonth(val yearMonth: YearMonth) : RecurringAction()
}
