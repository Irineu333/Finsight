package com.neoutils.finsight.ui.modal.viewRecurring

sealed class ViewRecurringEvent {
    data object Dismiss : ViewRecurringEvent()
}
