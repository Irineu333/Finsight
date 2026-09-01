package com.neoutils.finsight.ui.modal.viewCategory

/**
 * The detail states a period it determines itself, so nothing here moves one: there is
 * no month to advance or to go back to, and navigating time belongs to the transaction
 * list, which already does it.
 */
sealed class ViewCategoryAction {
    data object Unarchive : ViewCategoryAction()
}
