package com.neoutils.finsight.ui.screen.dashboard

import com.neoutils.finsight.util.UiText

data class DashboardEditItem(
    val key: String,
    val title: UiText,
    val preview: DashboardComponentVariant,
    val config: Map<String, String> = emptyMap(),
)
