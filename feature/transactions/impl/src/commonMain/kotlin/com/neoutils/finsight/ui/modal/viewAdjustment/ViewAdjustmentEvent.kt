package com.neoutils.finsight.ui.modal.viewAdjustment

sealed class ViewAdjustmentEvent {
    data object Dismiss : ViewAdjustmentEvent()
}
