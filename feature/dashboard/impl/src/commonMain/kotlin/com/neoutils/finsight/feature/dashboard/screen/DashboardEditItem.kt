package com.neoutils.finsight.feature.dashboard.screen

import com.neoutils.finsight.core.ui.util.UiText
data class DashboardEditItem(
    val preview: DashboardComponentVariant,
    val config: Map<String, String> = emptyMap(),
) {
    val key: String get() = preview.key
    val title: UiText get() = preview.title
}
