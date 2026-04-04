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
    ),
    CREDIT_CARD_BALANCE_STATS(
        key = "balance_stats_credit_card",
    ),
    ACCOUNTS_OVERVIEW(
        key = "accounts_overview",
        defaultConfig = mapOf(
            AccountsOverviewConfig.HIDE_SINGLE_ACCOUNT to "true",
        ),
    ),
    CREDIT_CARDS_PAGER(
        key = "credit_cards_pager",
    ),
    SPENDING_BY_CATEGORY(
        key = "spending_by_category",
    ),
    INCOME_BY_CATEGORY(
        key = "income_by_category",
    ),
    BUDGETS(
        key = "budgets",
    ),
    PENDING_RECURRING(
        key = "pending_recurring",
    ),
    RECENTS(
        key = "recents",
    ),
    QUICK_ACTIONS(
        key = "quick_actions",
        defaultConfig = mapOf(
            DashboardComponentConfig.SHOW_HEADER to "false",
        ),
    );

    companion object {
        fun fromKey(key: String): DashboardComponentType? = entries.find { it.key == key }
    }
}
