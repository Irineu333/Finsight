package com.neoutils.finance.ui.modal.viewCategory

sealed class ViewCategoryAction {
    data object NextMonth : ViewCategoryAction()
    data object PreviousMonth : ViewCategoryAction()
}