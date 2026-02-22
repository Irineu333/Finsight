package com.neoutils.finsight.ui.modal.viewCategory

sealed class ViewCategoryAction {
    data object NextMonth : ViewCategoryAction()
    data object PreviousMonth : ViewCategoryAction()
}