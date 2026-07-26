package com.neoutils.finsight.ui.modal.viewRecurring

sealed class ViewRecurringAction {
    data object Unarchive : ViewRecurringAction()
}
