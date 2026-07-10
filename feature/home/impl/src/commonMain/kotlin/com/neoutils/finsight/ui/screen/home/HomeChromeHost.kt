package com.neoutils.finsight.ui.screen.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.feature.dashboard.api.DashboardRoute
import com.neoutils.finsight.feature.home.api.HomeChromeConfig
import com.neoutils.finsight.feature.home.api.HomeGraph
import com.neoutils.finsight.feature.home.api.LocalHomeChromeController
import com.neoutils.finsight.feature.transactions.api.TransactionsEntry
import com.neoutils.finsight.navigation.LocalNavController
import com.neoutils.finsight.ui.component.BottomNavigationBar
import com.neoutils.finsight.ui.component.LocalModalManager
import org.koin.compose.koinInject

@Composable
fun HomeChromeHost(
    content: @Composable (PaddingValues) -> Unit,
) {
    val analytics = koinInject<Analytics>()
    val transactionsEntry = koinInject<TransactionsEntry>()
    val modalManager = LocalModalManager.current
    val navController = LocalNavController.current
    val homeChromeController = rememberHomeChromeStateHolder()

    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val destination = currentBackStackEntry?.destination

    val isHome = destination?.hierarchy?.any { it.hasRoute<HomeGraph>() } == true

    val selectedItem = NavigationItem.entries.firstOrNull { item ->
        destination?.hierarchy?.any { it.hasRoute(item.route::class) } == true
    } ?: NavigationItem.Dashboard

    LaunchedEffect(isHome, selectedItem) {
        if (isHome) {
            analytics.logScreenView(selectedItem.screenName)
        }
    }

    val homeChromeTransition = updateTransition(
        targetState = if (isHome) homeChromeController.config else HomeChromeConfig.ContentOnly,
        label = "HomeChromeTransition",
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
                        items = NavigationItem.entries,
                        selectedItem = selectedItem,
                        onItemSelected = { item ->
                            navController.navigate(item.route) {
                                launchSingleTop = true

                                popUpTo(DashboardRoute) {
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
                            modalManager.show(transactionsEntry.addTransactionModal())
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
            content = content,
        )
    }
}
