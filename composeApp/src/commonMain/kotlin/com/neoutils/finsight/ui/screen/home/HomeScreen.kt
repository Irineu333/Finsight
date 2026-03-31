@file:OptIn(ExperimentalSerializationApi::class, ExperimentalSharedTransitionApi::class)

package com.neoutils.finsight.ui.screen.home

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.neoutils.finsight.ui.modal.addTransaction.AddTransactionModal
import com.neoutils.finsight.ui.component.BottomNavigationBar
import com.neoutils.finsight.ui.component.LocalAnimatedVisibilityScope
import com.neoutils.finsight.ui.component.LocalModalManager
import com.neoutils.finsight.ui.component.NavigationItem
import com.neoutils.finsight.ui.component.NavigationDispatcherProvider
import com.neoutils.finsight.ui.component.ModalManagerHost
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.ui.component.SharedTransitionProvider
import com.neoutils.finsight.util.PerspectiveTabNavType
import com.neoutils.finsight.util.TransactionTargetNavType
import com.neoutils.finsight.util.TransactionTypeNavType
import com.neoutils.finsight.ui.screen.accounts.AccountsScreen
import com.neoutils.finsight.ui.screen.budgets.BudgetsScreen
import com.neoutils.finsight.ui.screen.categories.CategoriesScreen
import com.neoutils.finsight.ui.screen.creditCards.CreditCardsScreen
import com.neoutils.finsight.ui.screen.dashboard.DashboardScreen
import com.neoutils.finsight.ui.screen.installments.InstallmentsScreen
import com.neoutils.finsight.ui.screen.invoiceTransactions.InvoiceTransactionsScreen
import com.neoutils.finsight.ui.screen.recurring.RecurringScreen
import com.neoutils.finsight.ui.screen.report.config.ReportConfigScreen
import com.neoutils.finsight.ui.screen.report.viewer.ReportViewerScreen
import com.neoutils.finsight.ui.screen.transactions.TransactionsScreen
import com.neoutils.finsight.ui.screen.report.config.PerspectiveTab
import kotlinx.serialization.ExperimentalSerializationApi
import kotlin.reflect.typeOf

@Composable
fun AppNavHost() = Surface {

    val navController = rememberNavController()
    val navigationDispatcher = rememberAppNavigationDispatcher(navController)

    ModalManagerHost {
        NavigationDispatcherProvider(dispatcher = navigationDispatcher) {
            SharedTransitionProvider {
                NavHost(
                    navController = navController,
                    startDestination = AppRoute.Home,
                ) {
                    composable<AppRoute.Home> {
                        CompositionLocalProvider(
                            LocalAnimatedVisibilityScope provides this
                        ) {
                            HomeScreen()
                        }
                    }

                    composable<AppRoute.Categories> {
                        CategoriesScreen(
                            onNavigateBack = {
                                navController.navigateUp()
                            }
                        )
                    }

                    composable<AppRoute.CreditCards> { backStackEntry ->
                        val route = backStackEntry.toRoute<AppRoute.CreditCards>()
                        CompositionLocalProvider(
                            LocalAnimatedVisibilityScope provides this
                        ) {
                            CreditCardsScreen(
                                initialCreditCardId = route.creditCardId,
                                onNavigateBack = {
                                    navController.navigateUp()
                                }
                            )
                        }
                    }

                    composable<AppRoute.InvoiceTransactions> { backStackEntry ->
                        val route = backStackEntry.toRoute<AppRoute.InvoiceTransactions>()
                        InvoiceTransactionsScreen(
                            creditCardId = route.creditCardId,
                            onNavigateBack = {
                                navController.navigateUp()
                            }
                        )
                    }

                    composable<AppRoute.Accounts> { backStackEntry ->
                        val route = backStackEntry.toRoute<AppRoute.Accounts>()
                        AccountsScreen(
                            initialAccountId = route.accountId,
                            onNavigateBack = {
                                navController.navigateUp()
                            }
                        )
                    }

                    composable<AppRoute.Installments> {
                        InstallmentsScreen(
                            onNavigateBack = {
                                navController.navigateUp()
                            }
                        )
                    }

                    composable<AppRoute.Budgets> {
                        BudgetsScreen(
                            onNavigateBack = {
                                navController.navigateUp()
                            }
                        )
                    }

                    composable<AppRoute.Recurring> {
                        RecurringScreen(
                            onNavigateBack = {
                                navController.navigateUp()
                            }
                        )
                    }

                    composable<AppRoute.ReportConfig> {
                        ReportConfigScreen(
                            onNavigateBack = {
                                navController.navigateUp()
                            },
                            onNavigateToViewer = { route ->
                                navController.navigate(route)
                            },
                        )
                    }

                    composable<AppRoute.ReportViewer>(
                        typeMap = mapOf(
                            typeOf<PerspectiveTab>() to PerspectiveTabNavType()
                        )
                    ) { backStackEntry ->
                        val route = backStackEntry.toRoute<AppRoute.ReportViewer>()
                        ReportViewerScreen(
                            route = route,
                            onNavigateBack = {
                                navController.navigateUp()
                            },
                        )
                    }
                }
            }
        }
    }
}

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
