package com.neoutils.finsight.ui.modal.viewAccount

sealed class ViewAccountEvent {
    data object Dismiss : ViewAccountEvent()
}
