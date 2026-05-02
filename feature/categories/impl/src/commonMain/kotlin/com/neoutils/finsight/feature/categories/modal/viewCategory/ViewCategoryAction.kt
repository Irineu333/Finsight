package com.neoutils.finsight.feature.categories.modal.viewCategory

sealed class ViewCategoryAction {
    data object NextMonth : ViewCategoryAction()
    data object PreviousMonth : ViewCategoryAction()
}