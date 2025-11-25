package com.neoutils.finance.home

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.neoutils.finance.dashboard.DashboardScreen
import com.neoutils.finance.transactions.TransactionsScreen

@Composable
fun HomeScreen() = Surface {

    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = HomeRoute.Dashboard
    ) {
        composable<HomeRoute.Dashboard> {
            DashboardScreen(
                onAddTransaction = {
                    TODO("open add transaction model")
                }
            )
        }

        composable<HomeRoute.Transactions> {
            TransactionsScreen()
        }
    }
}
