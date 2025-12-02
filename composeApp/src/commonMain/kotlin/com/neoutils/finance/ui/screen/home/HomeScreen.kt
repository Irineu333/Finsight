@file:OptIn(ExperimentalSerializationApi::class)

package com.neoutils.finance.ui.screen.home

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.bundle.Bundle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import androidx.savedstate.SavedState
import androidx.savedstate.read
import androidx.savedstate.write
import com.neoutils.finance.ui.modal.AddTransactionModal
import com.neoutils.finance.ui.component.BottomNavigationBar
import com.neoutils.finance.ui.component.LocalModalManager
import com.neoutils.finance.ui.component.NavigationItem
import com.neoutils.finance.ui.component.ModalManagerHost
import com.neoutils.finance.domain.model.Transaction
import com.neoutils.finance.ui.screen.categories.CategoriesScreen
import com.neoutils.finance.ui.screen.dashboard.DashboardScreen
import com.neoutils.finance.ui.screen.transactions.TransactionsScreen
import kotlinx.serialization.ExperimentalSerializationApi
import kotlin.reflect.typeOf

class TransactionTypeNavType : NavType<Transaction.Type?>(isNullableAllowed = true) {
    override fun put(
        bundle: SavedState,
        key: String,
        value: Transaction.Type?
    ) {
        bundle.write {
            if (value != null) {
                putString(key, value.name)
            }
        }
    }

    override fun get(
        bundle: SavedState,
        key: String
    ): Transaction.Type {
        return bundle.read { Transaction.Type.valueOf(getString(key)) }
    }

    override fun parseValue(value: String): Transaction.Type {
        return Transaction.Type.valueOf(value)
    }
}

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

    val selectedItem = when {
        currentBackStackEntry?.destination?.route?.contains(
            HomeRoute.Dashboard.serializer().descriptor.serialName
        ) == true -> NavigationItem.Dashboard

        currentBackStackEntry?.destination?.route?.contains(
            HomeRoute.Transactions.serializer().descriptor.serialName
        ) == true -> NavigationItem.Transactions

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
                            NavigationItem.Transactions -> HomeRoute.Transactions()
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
                    openTransactions = { filterType ->
                        navController.navigate(HomeRoute.Transactions(filterType))
                    },
                    openCategories = openCategories,
                )
            }

            composable<HomeRoute.Transactions>(
                typeMap = mapOf(
                    typeOf<Transaction.Type?>() to TransactionTypeNavType()
                )
            ) { backStackEntry ->
                val route = backStackEntry.toRoute<HomeRoute.Transactions>()

                TransactionsScreen(initialFilterType = route.filterType)
            }
        }
    }

}
