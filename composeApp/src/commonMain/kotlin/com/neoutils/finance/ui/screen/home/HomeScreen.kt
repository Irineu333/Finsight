@file:OptIn(ExperimentalSerializationApi::class)

package com.neoutils.finance.ui.screen.home

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
import com.neoutils.finance.ui.modal.AddTransactionModal
import com.neoutils.finance.ui.component.BottomNavigationBar
import com.neoutils.finance.ui.component.LocalModalManager
import com.neoutils.finance.ui.component.NavigationItem
import com.neoutils.finance.ui.component.ModalManagerHost
import com.neoutils.finance.ui.screen.categories.CategoriesScreen
import com.neoutils.finance.ui.screen.dashboard.DashboardScreen
import com.neoutils.finance.ui.screen.transactions.TransactionsScreen
import kotlinx.serialization.ExperimentalSerializationApi

@Composable
fun AppNavHost() = Surface {

    ModalManagerHost {

        val navController = rememberNavController()

        NavHost(
            navController = navController,
            startDestination = AppRoute.Home,
        ) {
            composable<AppRoute.Home> {
                HomeScreen(
                    openCategories = {
                        navController.navigate(AppRoute.Categories)
                    }
                )
            }

            composable<AppRoute.Categories> {
                CategoriesScreen(
                    onNavigateBack = {
                        navController.navigateUp()
                    }
                )
            }
        }
    }
}

@Composable
fun HomeScreen(
    openCategories: () -> Unit = {},
) {
    val modalManager = LocalModalManager.current
    val navController = rememberNavController()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()

    val selectedItem = when (currentBackStackEntry?.destination?.route) {
        HomeRoute.Dashboard.serializer().descriptor.serialName -> NavigationItem.Dashboard
        HomeRoute.Transactions.serializer().descriptor.serialName -> NavigationItem.Transactions
        else -> NavigationItem.Dashboard
    }

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
                    openTransactions = {
                        navController.navigate(HomeRoute.Transactions)
                    },
                    openCategories = openCategories,
                )
            }

            composable<HomeRoute.Transactions> {
                TransactionsScreen()
            }
        }
    }

}
