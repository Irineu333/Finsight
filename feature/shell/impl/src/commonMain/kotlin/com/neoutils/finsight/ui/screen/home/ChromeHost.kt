package com.neoutils.finsight.ui.screen.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.window.core.layout.WindowSizeClass
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.feature.shell.api.ChromeConfig
import com.neoutils.finsight.feature.shell.api.LocalChromeController
import com.neoutils.finsight.feature.shell.api.NavCatalog
import com.neoutils.finsight.feature.shell.api.NavDestination as CatalogDestination
import com.neoutils.finsight.feature.transactions.api.TransactionsEntry
import com.neoutils.finsight.navigation.LocalNavController
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.add_transaction_fab_description
import com.neoutils.finsight.ui.component.BottomNavigationBar
import com.neoutils.finsight.ui.component.DetailPane
import com.neoutils.finsight.ui.component.LocalModalManager
import com.neoutils.finsight.ui.component.NavigationRailBar
import com.neoutils.finsight.ui.util.isExtraWideWindow
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
fun ChromeHost(
    content: @Composable (PaddingValues) -> Unit,
) {
    val analytics = koinInject<Analytics>()
    val transactionsEntry = koinInject<TransactionsEntry>()
    val navCatalog = koinInject<NavCatalog>()
    val modalManager = LocalModalManager.current
    val navController = LocalNavController.current
    val chromeController = rememberChromeStateHolder()

    val destinations = navCatalog.destinations
    val railItems = destinations.filter { !it.mobileOnly }
    val bottomItems = destinations.filter { it.primaryTab }

    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val destination = currentBackStackEntry?.destination

    // The active section, resolved in two tiers so the selector highlights correctly on any screen:
    // 1. Exact route match against the hierarchy — handles section roots and sibling destinations that
    //    share a graph but are distinct rail items (e.g. Credit Cards vs Installments).
    // 2. Fallback for pushed sub-destinations (e.g. an invoice screen), whose route is not in the
    //    catalog: match the catalog item that owns the current section's start destination.
    val selectedItem = destinations.firstOrNull { item ->
        destination?.hierarchy?.any { it.hasRoute(item.route::class) } == true
    } ?: destination?.hierarchy
        ?.firstNotNullOfOrNull { it as? NavGraph }
        ?.findStartDestination()
        ?.let { sectionStart -> destinations.firstOrNull { sectionStart.hasRoute(it.route::class) } }

    val isOnPrimaryTab = selectedItem?.primaryTab == true

    LaunchedEffect(selectedItem) {
        if (selectedItem?.primaryTab == true) {
            analytics.logScreenView(selectedItem.screenName)
        }
    }

    // Selecting a rail/bottom-bar item jumps to a top-level feature host, resetting to the dashboard
    // root so hosts never stack — the back stack stays "dashboard → host (→ its sub-features)".
    val onItemSelected: (CatalogDestination) -> Unit = { item ->
        navController.navigate(item.route) {
            popUpTo(navController.graph.findStartDestination().id) { inclusive = false }
            launchSingleTop = true
        }
    }

    val isWideWindow = currentWindowAdaptiveInfo().windowSizeClass
        .isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)

    // Note: the back affordance is owned by each screen (a feature's main screen hides it in wide
    // windows via `isWideWindow()`; pushed sub-destinations always show it), not propagated from here.

    // Visibility, per form factor:
    // - Desktop rail: persistent — visible unless the screen publishes `ContentOnly`.
    // - Mobile bottom bar: only on a primary tab, and unless the screen publishes `ContentOnly`.
    val effectiveConfig = when {
        isWideWindow -> chromeController.config
        isOnPrimaryTab -> chromeController.config
        else -> ChromeConfig.ContentOnly
    }

    val chromeTransition = updateTransition(
        targetState = effectiveConfig,
        label = "ChromeTransition",
    )

    val onAddTransaction = {
        modalManager.show(transactionsEntry.addTransactionModal())
    }

    CompositionLocalProvider(LocalChromeController provides chromeController) {
        Scaffold(
            contentWindowInsets = WindowInsets(),
            bottomBar = {
                if (!isWideWindow) {
                    chromeTransition.AnimatedVisibility(
                        visible = { it.isBottomBarVisible },
                        enter = slideInVertically { it } + expandVertically(),
                        exit = shrinkVertically() + slideOutVertically { it },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        BottomNavigationBar(
                            items = bottomItems,
                            selectedItem = selectedItem ?: bottomItems.first(),
                            onItemSelected = onItemSelected,
                        )
                    }
                }
            },
            floatingActionButton = {
                if (!isWideWindow) {
                    chromeTransition.AnimatedVisibility(
                        visible = { it.isFloatingActionButtonVisible },
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier
                            .offset(y = 40.dp)
                            .size(56.dp)
                    ) {
                        AddTransactionFab(onClick = onAddTransaction)
                    }
                }
            },
            floatingActionButtonPosition = FabPosition.Center,
        ) { padding ->
            Row(modifier = Modifier.fillMaxSize()) {
                if (isWideWindow) {
                    chromeTransition.AnimatedVisibility(
                        visible = { it.isBottomBarVisible },
                        enter = slideInHorizontally { -it } + expandHorizontally() + fadeIn(),
                        exit = shrinkHorizontally() + slideOutHorizontally { -it } + fadeOut(),
                    ) {
                        NavigationRailBar(
                            items = railItems,
                            selectedItem = selectedItem ?: railItems.first(),
                            onItemSelected = onItemSelected,
                            header = {
                                chromeTransition.AnimatedVisibility(
                                    visible = { it.isFloatingActionButtonVisible },
                                    enter = fadeIn(),
                                    exit = fadeOut(),
                                ) {
                                    AddTransactionFab(onClick = onAddTransaction)
                                }
                            },
                        )
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    content(padding)
                }

                if (isExtraWideWindow()) {
                    DetailPane(
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(top = padding.calculateTopPadding()),
                    )
                }
            }
        }
    }
}

private val CatalogDestination.screenName: String
    get() = route::class.simpleName
        .orEmpty()
        .removeSuffix("Route")
        .removeSuffix("Graph")
        .replaceFirstChar { it.lowercase() }

@Composable
private fun AddTransactionFab(
    onClick: () -> Unit,
) {
    FloatingActionButton(
        onClick = onClick,
        contentColor = Color.White,
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = stringResource(Res.string.add_transaction_fab_description),
            modifier = Modifier.size(24.dp)
        )
    }
}
