package com.neoutils.finsight.ui.modal.viewAccount

sealed class ViewAccountAction {
    data object Unarchive : ViewAccountAction()
}
