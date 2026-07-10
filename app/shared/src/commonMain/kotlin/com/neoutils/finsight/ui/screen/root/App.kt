package com.neoutils.finsight.ui.screen.root

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.auth.AuthService
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.extension.ProvidePlatformContext
import com.neoutils.finsight.navigation.LocalNavController
import com.neoutils.finsight.ui.component.BottomNavigationBar
import com.neoutils.finsight.ui.component.FormattingLocalsHost
import com.neoutils.finsight.ui.component.LocalModalManager
import com.neoutils.finsight.ui.component.ModalManagerHost
import com.neoutils.finsight.ui.component.SharedTransitionProvider
import com.neoutils.finsight.ui.modal.addTransaction.AddTransactionModal
import com.neoutils.finsight.ui.screen.home.HomeGraph
import com.neoutils.finsight.ui.screen.home.NavigationItem
import com.neoutils.finsight.ui.screen.home.HomeChromeConfig
import com.neoutils.finsight.ui.screen.home.LocalHomeChromeController
import com.neoutils.finsight.ui.screen.home.rememberHomeChromeStateHolder
import com.neoutils.finsight.ui.theme.FinsightTheme
import org.koin.compose.koinInject

@Composable
fun App() {
    val analytics = koinInject<Analytics>()
    val crashlytics = koinInject<Crashlytics>()
    val authService = koinInject<AuthService>()

    LaunchedEffect(Unit) {
        val userId = authService.getUserId()
        analytics.setUserId(userId)
        crashlytics.setUserId(userId)
    }

    FinsightTheme {
        Surface {
            ProvidePlatformContext {
                FormattingLocalsHost {
                    val navController = rememberNavController()

                    CompositionLocalProvider(LocalNavController provides navController) {
                        ModalManagerHost {
                            AppScaffold(navController)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppScaffold(navController: NavHostController) {
    val analytics = koinInject<Analytics>()
    val modalManager = LocalModalManager.current
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
                                restoreState = true

                                popUpTo(HomeGraph) {
                                    saveState = true
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
            SharedTransitionProvider {
                AppNavHost(
                    navController = navController,
                    modifier = Modifier.padding(paddingValues),
                )
            }
        }
    }
}
