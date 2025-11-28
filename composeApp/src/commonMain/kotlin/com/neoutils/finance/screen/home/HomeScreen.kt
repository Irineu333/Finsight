package com.neoutils.finance.screen.home

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.neoutils.finance.component.AddTransactionModal
import com.neoutils.finance.component.BottomNavigationBar
import com.neoutils.finance.component.NavigationItem
import com.neoutils.finance.manager.ModalManagerHost
import com.neoutils.finance.screen.dashboard.DashboardScreen
import com.neoutils.finance.screen.transactions.TransactionsScreen

@Composable
fun HomeScreen() = Surface {

    val navController = rememberNavController()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()

    val currentRoute = currentBackStackEntry?.destination?.route
    val selectedItem = when {
        currentRoute?.contains("Dashboard") == true -> NavigationItem.Dashboard
        currentRoute?.contains("Transactions") == true -> NavigationItem.Transactions
        else -> NavigationItem.Dashboard
    }

    ModalManagerHost { modalManager ->

        Scaffold(
            bottomBar = {
                BottomNavigationBar(
                    selectedItem = selectedItem,
                    onItemSelected = { item ->
                        navController.navigate(
                            when (item) {
                                NavigationItem.Dashboard -> HomeRoute.Dashboard
                                NavigationItem.Transactions -> HomeRoute.Transactions
                            }
                        )
                    },
                    onAddClick = {
                        modalManager.show(AddTransactionModal())
                    }
                )
            }
        ) { paddingValues ->
            NavHost(
                navController = navController,
                startDestination = HomeRoute.Dashboard,
                modifier = Modifier.padding(paddingValues)
            ) {
                composable<HomeRoute.Dashboard> {
                    DashboardScreen(
                        onSeeAllTransactions = {
                            navController.navigate(HomeRoute.Transactions)
                        },
                    )
                }

                composable<HomeRoute.Transactions> {
                    TransactionsScreen()
                }
            }
        }
    }
}
