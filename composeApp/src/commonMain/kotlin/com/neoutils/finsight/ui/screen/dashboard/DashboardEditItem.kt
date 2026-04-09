package com.neoutils.finsight.ui.screen.dashboard

import com.neoutils.finsight.util.UiText

data class DashboardEditItem(
    val preview: DashboardComponentVariant,
    val config: Map<String, String> = emptyMap(),
) {
    val key: String get() = preview.key
    val title: UiText get() = preview.title
}
