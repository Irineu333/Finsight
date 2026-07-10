package com.neoutils.finsight.ui.screen.dashboard

import com.neoutils.finsight.feature.accounts.api.AccountsRoute
import com.neoutils.finsight.feature.budgets.api.BudgetsRoute
import com.neoutils.finsight.feature.categories.api.CategoriesRoute
import com.neoutils.finsight.feature.creditcards.api.CreditCardsRoute
import com.neoutils.finsight.feature.creditcards.api.InstallmentsRoute
import com.neoutils.finsight.feature.recurring.api.RecurringRoute
import com.neoutils.finsight.feature.report.api.ReportGraph
import com.neoutils.finsight.feature.support.api.SupportGraph
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.dashboard_accounts
import com.neoutils.finsight.resources.dashboard_budgets
import com.neoutils.finsight.resources.dashboard_categories
import com.neoutils.finsight.resources.dashboard_credit_cards
import com.neoutils.finsight.resources.dashboard_installments
import com.neoutils.finsight.resources.dashboard_recurring
import com.neoutils.finsight.resources.dashboard_reports
import com.neoutils.finsight.resources.dashboard_support
import com.neoutils.finsight.util.UiText

enum class QuickActionType(
    val title: UiText,
    val route: Any,
) {
    BUDGETS(
        title = UiText.Res(Res.string.dashboard_budgets),
        route = BudgetsRoute,
    ),
    CATEGORIES(
        title = UiText.Res(Res.string.dashboard_categories),
        route = CategoriesRoute,
    ),
    CREDIT_CARDS(
        title = UiText.Res(Res.string.dashboard_credit_cards),
        route = CreditCardsRoute(),
    ),
    ACCOUNTS(
        title = UiText.Res(Res.string.dashboard_accounts),
        route = AccountsRoute(),
    ),
    RECURRING(
        title = UiText.Res(Res.string.dashboard_recurring),
        route = RecurringRoute,
    ),
    REPORTS(
        title = UiText.Res(Res.string.dashboard_reports),
        route = ReportGraph,
    ),
    INSTALLMENTS(
        title = UiText.Res(Res.string.dashboard_installments),
        route = InstallmentsRoute,
    ),
    SUPPORT(
        title = UiText.Res(Res.string.dashboard_support),
        route = SupportGraph,
    ),
}
