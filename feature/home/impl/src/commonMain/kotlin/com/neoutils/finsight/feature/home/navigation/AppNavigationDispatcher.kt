package com.neoutils.finsight.feature.home.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import com.neoutils.finsight.feature.home.component.NavigationDestination
import com.neoutils.finsight.feature.home.component.NavigationDispatcher
import com.neoutils.finsight.feature.home.route.AppRoute

class AppNavigationDispatcher(
    private val navController: NavHostController,
) : NavigationDispatcher {
    override fun dispatch(destination: NavigationDestination) {
        when (destination) {
            NavigationDestination.Categories -> {
                navController.navigate(AppRoute.Categories)
            }

            is NavigationDestination.InvoiceTransactions -> {
                navController.navigate(AppRoute.InvoiceTransactions(destination.creditCardId))
            }

            is NavigationDestination.CreditCards -> {
                navController.navigate(AppRoute.CreditCards(destination.creditCardId))
            }

            is NavigationDestination.Accounts -> {
                navController.navigate(AppRoute.Accounts(destination.accountId))
            }

            NavigationDestination.Installments -> {
                navController.navigate(AppRoute.Installments)
            }

            NavigationDestination.Budgets -> {
                navController.navigate(AppRoute.Budgets)
            }

            NavigationDestination.Recurring -> {
                navController.navigate(AppRoute.Recurring)
            }

            NavigationDestination.ReportConfig -> {
                navController.navigate(AppRoute.Reports)
            }

            NavigationDestination.Support -> {
                navController.navigate(AppRoute.Support)
            }
        }
    }
}

@Composable
fun rememberAppNavigationDispatcher(
    navController: NavHostController,
): NavigationDispatcher = remember(navController) {
    AppNavigationDispatcher(navController)
}
