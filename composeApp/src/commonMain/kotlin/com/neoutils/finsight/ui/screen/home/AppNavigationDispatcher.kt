package com.neoutils.finsight.ui.screen.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import com.neoutils.finsight.ui.component.NavigationDestination
import com.neoutils.finsight.ui.component.NavigationDispatcher

internal class AppNavigationDispatcher(
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
                navController.navigate(AppRoute.ReportConfig)
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
