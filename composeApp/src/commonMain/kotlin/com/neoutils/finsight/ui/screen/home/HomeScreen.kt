@file:OptIn(ExperimentalSerializationApi::class, ExperimentalSharedTransitionApi::class)

package com.neoutils.finsight.ui.screen.home

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.ui.component.BottomNavigationBar
import com.neoutils.finsight.ui.component.LocalModalManager
import com.neoutils.finsight.ui.component.NavigationItem
import com.neoutils.finsight.ui.modal.addTransaction.AddTransactionModal
import com.neoutils.finsight.ui.screen.dashboard.DashboardScreen
import com.neoutils.finsight.ui.screen.transactions.TransactionsScreen
import com.neoutils.finsight.util.TransactionTargetNavType
import com.neoutils.finsight.util.TransactionTypeNavType
import kotlinx.serialization.ExperimentalSerializationApi
import kotlin.reflect.typeOf

@Composable
fun HomeScreen() {
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
        contentWindowInsets = WindowInsets(),
        bottomBar = {
            BottomNavigationBar(
                selectedItem = selectedItem,
                onItemSelected = { item ->
                    navController.navigate(
                        route = when (item) {
                            NavigationItem.Dashboard -> HomeRoute.Dashboard
                            NavigationItem.Transactions -> HomeRoute.Transactions()
                        },
                    ) {
                        launchSingleTop = true

                        popUpTo(HomeRoute.Dashboard::class) {
                            inclusive = false
                        }
                    }
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
                    openTransactions = { filterType, filterTarget ->
                        navController.navigate(
                            HomeRoute.Transactions(
                                filterType = filterType,
                                filterTarget = filterTarget,
                            )
                        )
                    },
                )
            }

            composable<HomeRoute.Transactions>(
                typeMap = mapOf(
                    typeOf<Transaction.Type?>() to TransactionTypeNavType(),
                    typeOf<Transaction.Target?>() to TransactionTargetNavType()
                )
            ) { backStackEntry ->
                val route = backStackEntry.toRoute<HomeRoute.Transactions>()

                TransactionsScreen(
                    categoryType = route.filterType,
                    target = route.filterTarget
                )
            }
        }
    }
}
