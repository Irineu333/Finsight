package com.neoutils.finsight.feature.dashboard.constant

object DashboardComponentConfig {
    const val TOP_SPACING = "top_spacing"
    const val SHOW_HEADER = "show_header"
    const val SHOW_EMPTY_STATE = "show_empty_state"
    const val HIDE_WHEN_EMPTY = "hide_when_empty"
}

fun Map<String, String>.hideWhenEmpty(defaultValue: Boolean): Boolean =
    get(DashboardComponentConfig.HIDE_WHEN_EMPTY)?.toBoolean() ?: defaultValue

fun Map<String, String>.showHeader(defaultValue: Boolean = true): Boolean =
    get(DashboardComponentConfig.SHOW_HEADER)?.toBoolean() ?: defaultValue
