package com.neoutils.finsight.ui.screen.dashboard

enum class DashboardComponentType(
    val key: String,
    val defaultConfig: Map<String, String> = emptyMap(),
) {
    TOTAL_BALANCE(
        key = "total_balance",
    ),
    CONCRETE_BALANCE_STATS(
        key = "balance_stats_concrete",
    ),
    PENDING_BALANCE_STATS(
        key = "balance_stats_pending",
        defaultConfig = mapOf(DashboardComponentConfig.HIDE_WHEN_EMPTY to "true"),
    ),
    CREDIT_CARD_BALANCE_STATS(
        key = "balance_stats_credit_card",
        defaultConfig = mapOf(DashboardComponentConfig.HIDE_WHEN_EMPTY to "true"),
    ),
    ACCOUNTS_OVERVIEW(
        key = "accounts_overview",
        defaultConfig = mapOf(
            DashboardComponentConfig.TOP_SPACING to "true",
            AccountsOverviewConfig.HIDE_SINGLE_ACCOUNT to "true",
        ),
    ),
    CREDIT_CARDS_PAGER(
        key = "credit_cards_pager",
        defaultConfig = mapOf(DashboardComponentConfig.TOP_SPACING to "true"),
    ),
    SPENDING_BY_CATEGORY(
        key = "spending_by_category",
        defaultConfig = mapOf(DashboardComponentConfig.TOP_SPACING to "true"),
    ),
    INCOME_BY_CATEGORY(
        key = "income_by_category",
        defaultConfig = mapOf(DashboardComponentConfig.TOP_SPACING to "true"),
    ),
    BUDGETS(
        key = "budgets",
        defaultConfig = mapOf(DashboardComponentConfig.TOP_SPACING to "true"),
    ),
    PENDING_RECURRING(
        key = "pending_recurring",
        defaultConfig = mapOf(DashboardComponentConfig.TOP_SPACING to "true"),
    ),
    RECENTS(
        key = "recents",
        defaultConfig = mapOf(DashboardComponentConfig.TOP_SPACING to "true"),
    ),
    QUICK_ACTIONS(
        key = "quick_actions",
        defaultConfig = mapOf(
            DashboardComponentConfig.TOP_SPACING to "true",
            DashboardComponentConfig.SHOW_HEADER to "false",
        ),
    );

    companion object {
        fun fromKey(key: String): DashboardComponentType? = entries.find { it.key == key }
    }
}
