@file:OptIn(ExperimentalSerializationApi::class, ExperimentalSharedTransitionApi::class)

package com.neoutils.finsight.ui.screen.home

import androidx.compose.animation.*
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.ui.component.*
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
    val homeChromeController = rememberHomeChromeStateHolder()

    val selectedItem = when {
        currentBackStackEntry?.destination?.route?.contains(
            HomeRoute.Dashboard.serializer().descriptor.serialName
        ) == true -> NavigationItem.Dashboard

        currentBackStackEntry?.destination?.route?.contains(
            HomeRoute.Transactions.serializer().descriptor.serialName
        ) == true -> NavigationItem.Transactions

        else -> NavigationItem.Dashboard
    }

    val homeChromeTransition = updateTransition(
        targetState = when (selectedItem) {
            NavigationItem.Dashboard -> homeChromeController.config
            NavigationItem.Transactions -> HomeChromeConfig.Default
        },
        label = "HomeChromeTransition"
    )

    CompositionLocalProvider(LocalHomeChromeController provides homeChromeController) {
        Scaffold(
            contentWindowInsets = WindowInsets(),
            bottomBar = {
                homeChromeTransition.AnimatedVisibility(
                    visible = { it.isBottomBarVisible },
                    enter = slideInVertically { it } + expandVertically(),
                    exit = shrinkVertically() + slideOutVertically { it },
                    modifier = Modifier.fillMaxWidth(),
                ) {
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
                        }
                    )
                }
            },
            floatingActionButton = {
                homeChromeTransition.AnimatedVisibility(
                    visible = { it.isFloatingActionButtonVisible },
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .offset(y = 40.dp)
                        .size(56.dp)
                ) {
                    FloatingActionButton(
                        onClick = {
                            modalManager.show(AddTransactionModal())
                        },
                        contentColor = Color.White,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            },
            floatingActionButtonPosition = FabPosition.Center,
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
}
