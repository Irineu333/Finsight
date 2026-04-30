package com.neoutils.finsight.ui.component

object BottomNavTestTags {
    fun item(item: NavigationItem): String = when (item) {
        NavigationItem.Dashboard -> "bottom-nav-dashboard"
        NavigationItem.Transactions -> "bottom-nav-transactions"
    }
}
