package com.neoutils.finsight.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.SupportAgent
import com.neoutils.finsight.feature.accounts.api.AccountsRoute
import com.neoutils.finsight.feature.budgets.api.BudgetsRoute
import com.neoutils.finsight.feature.categories.api.CategoriesRoute
import com.neoutils.finsight.feature.creditcards.api.CreditCardsRoute
import com.neoutils.finsight.feature.creditcards.api.InstallmentsRoute
import com.neoutils.finsight.feature.dashboard.api.DashboardRoute
import com.neoutils.finsight.feature.recurring.api.RecurringRoute
import com.neoutils.finsight.feature.report.api.ReportGraph
import com.neoutils.finsight.feature.shell.api.NavCatalog
import com.neoutils.finsight.feature.shell.api.NavDestination
import com.neoutils.finsight.feature.support.api.SupportGraph
import com.neoutils.finsight.feature.transactions.api.TransactionsRoute
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.dashboard_accounts
import com.neoutils.finsight.resources.dashboard_budgets
import com.neoutils.finsight.resources.dashboard_categories
import com.neoutils.finsight.resources.dashboard_recurring
import com.neoutils.finsight.resources.dashboard_reports
import com.neoutils.finsight.resources.dashboard_support
import com.neoutils.finsight.resources.nav_credit_cards
import com.neoutils.finsight.resources.nav_dashboard
import com.neoutils.finsight.resources.nav_installments
import com.neoutils.finsight.resources.nav_transactions

/**
 * Concrete destination catalog. Order matters: the mobile grid preserves the catalog order of
 * `!primaryTab` destinations. Primary tabs come first, followed by the feature sections, and finally
 * the Support entry (shown on every platform, including the desktop rail).
 */
internal object AppNavCatalog : NavCatalog {
    override val destinations: List<NavDestination> = listOf(
        NavDestination(
            icon = Icons.Default.Dashboard,
            labelRes = Res.string.nav_dashboard,
            route = DashboardRoute,
            primaryTab = true,
        ),
        NavDestination(
            icon = Icons.Default.Receipt,
            labelRes = Res.string.nav_transactions,
            route = TransactionsRoute(),
            primaryTab = true,
        ),
        NavDestination(
            icon = Icons.Default.Savings,
            labelRes = Res.string.dashboard_budgets,
            route = BudgetsRoute,
        ),
        NavDestination(
            icon = Icons.Default.Category,
            labelRes = Res.string.dashboard_categories,
            route = CategoriesRoute,
        ),
        NavDestination(
            icon = Icons.Default.CreditCard,
            labelRes = Res.string.nav_credit_cards,
            route = CreditCardsRoute(),
        ),
        NavDestination(
            icon = Icons.Default.AccountBalanceWallet,
            labelRes = Res.string.dashboard_accounts,
            route = AccountsRoute(),
        ),
        NavDestination(
            icon = Icons.Default.Autorenew,
            labelRes = Res.string.dashboard_recurring,
            route = RecurringRoute,
        ),
        NavDestination(
            icon = Icons.Default.Assessment,
            labelRes = Res.string.dashboard_reports,
            route = ReportGraph,
        ),
        NavDestination(
            icon = Icons.Default.CalendarMonth,
            labelRes = Res.string.nav_installments,
            route = InstallmentsRoute,
        ),
        NavDestination(
            icon = Icons.Default.SupportAgent,
            labelRes = Res.string.dashboard_support,
            route = SupportGraph,
        ),
    )
}
