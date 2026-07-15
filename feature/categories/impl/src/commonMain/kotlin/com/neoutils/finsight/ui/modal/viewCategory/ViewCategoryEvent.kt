package com.neoutils.finsight.ui.modal.viewCategory

sealed class ViewCategoryEvent {
    data object Dismiss : ViewCategoryEvent()
}
