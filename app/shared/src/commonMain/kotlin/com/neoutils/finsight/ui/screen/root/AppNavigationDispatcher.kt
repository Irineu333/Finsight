package com.neoutils.finsight.ui.screen.root

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import com.neoutils.finsight.ui.component.NavigationDestination
import com.neoutils.finsight.ui.component.NavigationDispatcher
import com.neoutils.finsight.feature.accounts.api.AccountsRoute
import com.neoutils.finsight.feature.budgets.api.BudgetsRoute
import com.neoutils.finsight.feature.categories.api.CategoriesRoute
import com.neoutils.finsight.feature.creditcards.api.CreditCardsRoute
import com.neoutils.finsight.feature.creditcards.api.InstallmentsRoute
import com.neoutils.finsight.feature.creditcards.api.InvoiceTransactionsRoute
import com.neoutils.finsight.feature.recurring.api.RecurringRoute
import com.neoutils.finsight.feature.report.api.ReportsRoute
import com.neoutils.finsight.feature.support.api.SupportRoute
import com.neoutils.finsight.ui.screen.home.AppRoute

internal class AppNavigationDispatcher(
    private val navController: NavHostController,
) : NavigationDispatcher {
    override fun dispatch(destination: NavigationDestination) {
        when (destination) {
            NavigationDestination.Categories -> {
                navController.navigate(CategoriesRoute)
            }

            is NavigationDestination.InvoiceTransactions -> {
                navController.navigate(InvoiceTransactionsRoute(destination.creditCardId))
            }

            is NavigationDestination.CreditCards -> {
                navController.navigate(CreditCardsRoute(destination.creditCardId))
            }

            is NavigationDestination.Accounts -> {
                navController.navigate(AccountsRoute(destination.accountId))
            }

            NavigationDestination.Installments -> {
                navController.navigate(InstallmentsRoute)
            }

            NavigationDestination.Budgets -> {
                navController.navigate(BudgetsRoute)
            }

            NavigationDestination.Recurring -> {
                navController.navigate(RecurringRoute)
            }

            NavigationDestination.ReportConfig -> {
                navController.navigate(ReportsRoute)
            }

            NavigationDestination.Support -> {
                navController.navigate(SupportRoute)
            }
        }
    }
}

@Composable
internal fun rememberAppNavigationDispatcher(
    navController: NavHostController,
): NavigationDispatcher = remember(navController) {
    AppNavigationDispatcher(navController)
}
