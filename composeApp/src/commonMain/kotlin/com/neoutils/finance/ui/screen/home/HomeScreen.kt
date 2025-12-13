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
import androidx.navigation.toRoute
import com.neoutils.finance.ui.modal.addTransaction.AddTransactionModal
import com.neoutils.finance.ui.component.BottomNavigationBar
import com.neoutils.finance.ui.component.LocalModalManager
import com.neoutils.finance.ui.component.NavigationItem
import com.neoutils.finance.ui.component.ModalManagerHost
import com.neoutils.finance.domain.model.Transaction
import com.neoutils.finance.util.TransactionTargetNavType
import com.neoutils.finance.util.TransactionTypeNavType
import com.neoutils.finance.ui.screen.categories.CategoriesScreen
import com.neoutils.finance.ui.screen.creditCards.CreditCardsScreen
import com.neoutils.finance.ui.screen.dashboard.DashboardScreen
import com.neoutils.finance.ui.screen.transactions.TransactionsScreen
import kotlinx.serialization.ExperimentalSerializationApi
import kotlin.reflect.typeOf

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
                    },
                    openCreditCards = {
                        navController.navigate(AppRoute.CreditCards)
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

            composable<AppRoute.CreditCards> {
                CreditCardsScreen(
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
    openCreditCards: () -> Unit = {},
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
                    openTransactions = { filterType, filterTarget ->
                        navController.navigate(
                            HomeRoute.Transactions(
                                filterType = filterType,
                                filterTarget = filterTarget
                            )
                        )
                    },
                    openCategories = openCategories,
                    openCreditCards = openCreditCards,
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

