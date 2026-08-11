package com.neoutils.finsight.ui.screen.dashboard

object DashboardComponentConfig {
    const val TOP_SPACING = "top_spacing"
    const val SHOW_HEADER = "show_header"
    const val SHOW_EMPTY_STATE = "show_empty_state"
    const val HIDE_WHEN_EMPTY = "hide_when_empty"
}

object TotalBalanceConfig {
    const val EXCLUDED_ACCOUNT_IDS = "excluded_account_ids"
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

object IncomeByCategoryConfig {
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

/**
 * The set of facade ids a widget was told to leave out, under [key] — comma-separated
 * `Long`s, the format every exclusion preference is written in. Absent, blank or
 * unparsable means nothing is excluded: a preference is not a place to fail from, and an
 * id matching no row excludes nothing anyway.
 */
fun Map<String, String>.excludedIds(key: String): Set<Long> =
    get(key)?.split(",")?.mapNotNullTo(mutableSetOf()) { it.toLongOrNull() } ?: emptySet()

fun Map<String, String>.hideWhenEmpty(defaultValue: Boolean): Boolean =
    get(DashboardComponentConfig.HIDE_WHEN_EMPTY)?.toBoolean() ?: defaultValue

fun Map<String, String>.showHeader(defaultValue: Boolean = true): Boolean =
    get(DashboardComponentConfig.SHOW_HEADER)?.toBoolean() ?: defaultValue

/**
 * The header's fallback for a preference saved before the widget declared one: the
 * widget's own `defaultConfig`, so the default is stated in a single place — a dashboard
 * assembled earlier keeps the appearance it had.
 */
fun Map<String, String>.showHeader(key: String): Boolean =
    showHeader(defaultValue = DashboardComponentType.fromKey(key)?.defaultConfig?.showHeader() ?: true)
