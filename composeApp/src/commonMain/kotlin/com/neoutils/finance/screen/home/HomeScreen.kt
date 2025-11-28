package com.neoutils.finance.screen.home

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.neoutils.finance.component.AddTransactionModal
import com.neoutils.finance.manager.ModalManagerHost
import com.neoutils.finance.screen.dashboard.DashboardScreen
import com.neoutils.finance.screen.transactions.TransactionsScreen

@Composable
fun HomeScreen() = Surface {

    val navController = rememberNavController()

    ModalManagerHost { modalManager ->

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
                    onNavigateBack = {
                        navController.navigateUp()
                    }
                )
            }
        }
    }
}
