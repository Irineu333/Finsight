package com.neoutils.finsight.ui.screen.recurring

sealed class RecurringAction {
    data class SelectFilter(val filter: RecurringFilter) : RecurringAction()
    data class SelectStatusFilter(val filter: RecurringStatusFilter) : RecurringAction()
}
