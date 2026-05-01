package com.neoutils.finsight.ui.screen.dashboard

enum class DashboardComponentType(
    val key: String,
    val defaultConfig: Map<String, String> = emptyMap(),
) {
    TOTAL_BALANCE(
        key = "total_balance",
        defaultConfig = mapOf(
            DashboardComponentConfig.TOP_SPACING to "false",
        ),
    ),
    CONCRETE_BALANCE_STATS(
        key = "balance_stats_concrete",
        defaultConfig = mapOf(
            DashboardComponentConfig.TOP_SPACING to "false",
            DashboardComponentConfig.HIDE_WHEN_EMPTY to "false",
        ),
    ),
    PENDING_BALANCE_STATS(
        key = "balance_stats_pending",
        defaultConfig = mapOf(
            DashboardComponentConfig.TOP_SPACING to "false",
            DashboardComponentConfig.HIDE_WHEN_EMPTY to "false",
        ),
    ),
    CREDIT_CARD_BALANCE_STATS(
        key = "balance_stats_credit_card",
        defaultConfig = mapOf(
            DashboardComponentConfig.TOP_SPACING to "false",
            DashboardComponentConfig.HIDE_WHEN_EMPTY to "false",
        ),
    ),
    ACCOUNTS_OVERVIEW(
        key = "accounts_overview",
        defaultConfig = mapOf(
            DashboardComponentConfig.TOP_SPACING to "false",
            DashboardComponentConfig.SHOW_HEADER to "true",
            AccountsOverviewConfig.HIDE_SINGLE_ACCOUNT to "true",
            AccountsOverviewConfig.EXCLUDED_ACCOUNT_IDS to "",
        ),
    ),
    CREDIT_CARDS_PAGER(
        key = "credit_cards_pager",
        defaultConfig = mapOf(
            DashboardComponentConfig.TOP_SPACING to "false",
            DashboardComponentConfig.SHOW_HEADER to "true",
            DashboardComponentConfig.SHOW_EMPTY_STATE to "true",
            CreditCardsPagerConfig.EXCLUDED_CARD_IDS to "",
        ),
    ),
    SPENDING_BY_CATEGORY(
        key = "spending_by_category",
        defaultConfig = mapOf(
            DashboardComponentConfig.TOP_SPACING to "false",
            SpendingByCategoryConfig.MAX_CATEGORIES to SpendingByCategoryConfig.ALL,
        ),
    ),
    INCOME_BY_CATEGORY(
        key = "income_by_category",
        defaultConfig = mapOf(
            DashboardComponentConfig.TOP_SPACING to "false",
            IncomeByCategoryConfig.MAX_CATEGORIES to IncomeByCategoryConfig.ALL,
        ),
    ),
    BUDGETS(
        key = "budgets",
        defaultConfig = mapOf(
            DashboardComponentConfig.TOP_SPACING to "false",
        ),
    ),
    PENDING_RECURRING(
        key = "pending_recurring",
        defaultConfig = mapOf(
            DashboardComponentConfig.TOP_SPACING to "false",
            DashboardComponentConfig.SHOW_HEADER to "true",
            PendingRecurringConfig.UPCOMING_DAYS_AHEAD to PendingRecurringConfig.DEFAULT_UPCOMING_DAYS_AHEAD.toString(),
        ),
    ),
    RECENTS(
        key = "recents",
        defaultConfig = mapOf(
            DashboardComponentConfig.TOP_SPACING to "false",
            DashboardComponentConfig.SHOW_HEADER to "true",
            RecentsConfig.COUNT to RecentsConfig.DEFAULT_COUNT.toString(),
        ),
    ),
    QUICK_ACTIONS(
        key = "quick_actions",
        defaultConfig = mapOf(
            DashboardComponentConfig.TOP_SPACING to "false",
            DashboardComponentConfig.SHOW_HEADER to "true",
            QuickActionsConfig.HIDDEN_ACTIONS to "",
        ),
    );

    companion object {
        fun fromKey(key: String): DashboardComponentType? = entries.find { it.key == key }
    }
}
