package com.neoutils.finsight.feature.dashboard.screen

import com.neoutils.finsight.feature.dashboard.resources.Res
import com.neoutils.finsight.feature.dashboard.resources.dashboard_accounts
import com.neoutils.finsight.feature.dashboard.resources.dashboard_budgets
import com.neoutils.finsight.feature.dashboard.resources.dashboard_categories
import com.neoutils.finsight.feature.dashboard.resources.dashboard_credit_cards
import com.neoutils.finsight.feature.dashboard.resources.dashboard_installments
import com.neoutils.finsight.feature.dashboard.resources.dashboard_recurring
import com.neoutils.finsight.feature.dashboard.resources.dashboard_reports
import com.neoutils.finsight.feature.dashboard.resources.dashboard_support
import com.neoutils.finsight.feature.home.component.NavigationDestination
import com.neoutils.finsight.core.ui.util.UiText
import com.neoutils.finsight.feature.recurring.model.Recurring
enum class QuickActionType(
    val title: UiText,
    val destination: NavigationDestination,
) {
    BUDGETS(
        title = UiText.Res(Res.string.dashboard_budgets),
        destination = NavigationDestination.Budgets,
    ),
    CATEGORIES(
        title = UiText.Res(Res.string.dashboard_categories),
        destination = NavigationDestination.Categories,
    ),
    CREDIT_CARDS(
        title = UiText.Res(Res.string.dashboard_credit_cards),
        destination = NavigationDestination.CreditCards(),
    ),
    ACCOUNTS(
        title = UiText.Res(Res.string.dashboard_accounts),
        destination = NavigationDestination.Accounts(),
    ),
    RECURRING(
        title = UiText.Res(Res.string.dashboard_recurring),
        destination = NavigationDestination.Recurring,
    ),
    REPORTS(
        title = UiText.Res(Res.string.dashboard_reports),
        destination = NavigationDestination.ReportConfig,
    ),
    INSTALLMENTS(
        title = UiText.Res(Res.string.dashboard_installments),
        destination = NavigationDestination.Installments,
    ),
    SUPPORT(
        title = UiText.Res(Res.string.dashboard_support),
        destination = NavigationDestination.Support,
    ),
}
