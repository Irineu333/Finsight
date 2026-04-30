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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.ui.component.BottomNavigationBar
import com.neoutils.finsight.ui.component.NavigationItem
import com.neoutils.finsight.ui.screen.dashboard.DashboardEntry
import com.neoutils.finsight.ui.screen.transactions.TransactionsEntry
import kotlinx.serialization.ExperimentalSerializationApi
import org.koin.compose.koinInject

@Composable
fun HomeScreen(
    onAddTransaction: () -> Unit = {},
    analytics: Analytics = koinInject(),
    dashboardEntry: DashboardEntry = koinInject(),
    transactionsEntry: TransactionsEntry = koinInject(),
) {
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

    LaunchedEffect(Unit) {
        analytics.logScreenView("home")
    }

    LaunchedEffect(selectedItem) {
        analytics.logScreenView(selectedItem.screenName)
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
                        onClick = onAddTransaction,
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
                with(dashboardEntry) {
                    register(
                        navController = navController,
                        onOpenTransactions = { filterType, filterTarget ->
                            navController.navigate(
                                HomeRoute.Transactions(
                                    filterType = filterType,
                                    filterTarget = filterTarget,
                                )
                            )
                        },
                    )
                }
                with(transactionsEntry) {
                    register(navController = navController)
                }
            }
        }
    }
}
