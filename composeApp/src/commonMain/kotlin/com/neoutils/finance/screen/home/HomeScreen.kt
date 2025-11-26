package com.neoutils.finance.screen.home

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.neoutils.finance.component.AddTransactionModal
import com.neoutils.finance.manager.ModalManager
import com.neoutils.finance.screen.dashboard.DashboardScreen
import com.neoutils.finance.screen.transactions.TransactionsScreen


@Composable
fun HomeScreen() = Surface {

    val navController = rememberNavController()
    val modal = remember { ModalManager() }

    NavHost(
        navController = navController,
        startDestination = HomeRoute.Dashboard
    ) {
        composable<HomeRoute.Dashboard> {
            DashboardScreen(
                onAddTransaction = {
                    modal.show(
                        AddTransactionModal(
                            onSave = {}
                        )
                    )
                }
            )
        }

        composable<HomeRoute.Transactions> {
            TransactionsScreen()
        }
    }

    modal.Content()
}
