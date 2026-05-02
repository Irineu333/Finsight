package com.neoutils.finsight.feature.recurring.screen

sealed class RecurringAction {
    data class SelectFilter(val filter: RecurringFilter) : RecurringAction()
    data class SelectStatusFilter(val filter: RecurringStatusFilter) : RecurringAction()
}
