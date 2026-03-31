package com.neoutils.finsight.ui.screen.dashboard

object DashboardComponentConfig {
    const val TOP_SPACING = "top_spacing"
    const val SHOW_HEADER = "show_header"
    const val SHOW_EMPTY_STATE = "show_empty_state"
    const val HIDE_WHEN_EMPTY = "hide_when_empty"
}

object AccountsOverviewConfig {
    const val EXCLUDED_ACCOUNT_IDS = "excluded_account_ids"
    const val HIDE_SINGLE_ACCOUNT = "hide_single_account"
}

object CreditCardsPagerConfig {
    const val EXCLUDED_CARD_IDS = "excluded_card_ids"
}

object SpendingByCategoryConfig {
    const val MAX_CATEGORIES = "max_categories"
    const val ALL = "-1"
}

object PendingRecurringConfig {
    const val UPCOMING_DAYS_AHEAD = "upcoming_days_ahead"
    const val DEFAULT_UPCOMING_DAYS_AHEAD = 0
}

object RecentsConfig {
    const val COUNT = "count"
    const val DEFAULT_COUNT = 4
}

object QuickActionsConfig {
    const val HIDDEN_ACTIONS = "hidden_actions"
}

fun Map<String, String>.hideWhenEmpty(defaultValue: Boolean): Boolean =
    get(DashboardComponentConfig.HIDE_WHEN_EMPTY)?.toBoolean() ?: defaultValue

fun Map<String, String>.showHeader(defaultValue: Boolean = true): Boolean =
    get(DashboardComponentConfig.SHOW_HEADER)?.toBoolean() ?: defaultValue
