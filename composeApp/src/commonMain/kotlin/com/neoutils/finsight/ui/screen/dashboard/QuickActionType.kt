package com.neoutils.finsight.ui.screen.dashboard

import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.dashboard_accounts
import com.neoutils.finsight.resources.dashboard_budgets
import com.neoutils.finsight.resources.dashboard_categories
import com.neoutils.finsight.resources.dashboard_credit_cards
import com.neoutils.finsight.resources.dashboard_installments
import com.neoutils.finsight.resources.dashboard_recurring
import com.neoutils.finsight.resources.dashboard_reports
import com.neoutils.finsight.resources.dashboard_support
import com.neoutils.finsight.ui.component.NavigationDestination
import com.neoutils.finsight.util.UiText

enum class QuickActionType(
    val title: UiText,
    val destination: NavigationDestination,
    val tag: String,
) {
    BUDGETS(
        title = UiText.Res(Res.string.dashboard_budgets),
        destination = NavigationDestination.Budgets,
        tag = "budgets",
    ),
    CATEGORIES(
        title = UiText.Res(Res.string.dashboard_categories),
        destination = NavigationDestination.Categories,
        tag = "categories",
    ),
    CREDIT_CARDS(
        title = UiText.Res(Res.string.dashboard_credit_cards),
        destination = NavigationDestination.CreditCards(),
        tag = "credit-cards",
    ),
    ACCOUNTS(
        title = UiText.Res(Res.string.dashboard_accounts),
        destination = NavigationDestination.Accounts(),
        tag = "accounts",
    ),
    RECURRING(
        title = UiText.Res(Res.string.dashboard_recurring),
        destination = NavigationDestination.Recurring,
        tag = "recurring",
    ),
    REPORTS(
        title = UiText.Res(Res.string.dashboard_reports),
        destination = NavigationDestination.ReportConfig,
        tag = "reports",
    ),
    INSTALLMENTS(
        title = UiText.Res(Res.string.dashboard_installments),
        destination = NavigationDestination.Installments,
        tag = "installments",
    ),
    SUPPORT(
        title = UiText.Res(Res.string.dashboard_support),
        destination = NavigationDestination.Support,
        tag = "support",
    ),
}
