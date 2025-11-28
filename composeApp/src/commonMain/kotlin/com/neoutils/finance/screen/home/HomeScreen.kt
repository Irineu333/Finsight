package com.neoutils.finance.screen.home

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.neoutils.finance.component.AddTransactionModal
import com.neoutils.finance.manager.LocalModalManager
import com.neoutils.finance.manager.ModalManager
import com.neoutils.finance.screen.dashboard.DashboardScreen
import com.neoutils.finance.screen.transactions.TransactionsScreen

@Composable
fun HomeScreen() = Surface {

    val navController = rememberNavController()
    val modalManager = remember { ModalManager() }

    CompositionLocalProvider(
        LocalModalManager provides modalManager
    ) {
        NavHost(
            navController = navController,
            startDestination = HomeRoute.Dashboard
        ) {
            composable<HomeRoute.Dashboard> {
                DashboardScreen(
                    onAddTransaction = {
                        modalManager.show(AddTransactionModal())
                    },
                    onSeeAllTransactions = {
                        navController.navigate(HomeRoute.Transactions)
                    }
                )
            }

            composable<HomeRoute.Transactions> {
                TransactionsScreen(
                    onNavigateBack = { navController.navigateUp() }
                )
            }
        }

        modalManager.Content()
    }
}
